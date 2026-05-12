package com.ai.scheduler.schedulers;

import com.ai.scheduler.dto.calendar.CalendarColor;
import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.calendar.scheduler.SchedulerEvent;
import com.ai.scheduler.dto.calendar.scheduler.SchedulingRequest;
import com.ai.scheduler.dto.llm.TaskDTO;
import com.ai.scheduler.dto.time.TimeRange;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Deterministic, proportional time-allocation scheduler — no LLM required.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Collect all constraint intervals (existing calendar events, sleep schedule)
 *       from the request — these are fetched from Google Calendar upstream and passed
 *       in as {@link ScheduleConstraint} objects.</li>
 *   <li>For every calendar day in the scheduling range, compute free slots =
 *       day-window minus all overlapping constraints.</li>
 *   <li>{@code totalFreeMinutes} = Σ free-slot durations across all days.</li>
 *   <li>{@code totalTaskMinutes = totalFreeMinutes × percentageOfTimeToUse}.</li>
 *   <li>Allocate each task a share of {@code totalTaskMinutes} proportional to its
 *       priority score (higher score → more time).</li>
 *   <li>Divide each task's total allocation equally across all days; further split
 *       each daily share into sessions ≤ {@code maxSessionMinutes} (default 60 min).
 *       This guarantees that every day has approximately the same scheduled workload
 *       and the same relative free-time ratio.</li>
 *   <li>Greedy placement per day: sessions are <em>interleaved by task</em> (round-robin,
 *       highest-priority task first in each round) and placed with <em>equal gaps</em>
 *       between them so that free time is spread uniformly across the day rather than
 *       bunched at the end.  Sessions that don't fit are carried forward to the next
 *       day.</li>
 *   <li>Priority score is mapped 1-to-1 onto a Google Calendar color so that the
 *       color of every event directly communicates its importance.</li>
 * </ol>
 *
 * <p>The maximum session length is controllable via the
 * {@link #ProportionalScheduler(int)} constructor (minimum {@value #MIN_SESSION_MINUTES}
 * minutes); the no-arg constructor uses {@value #DEFAULT_MAX_SESSION_MINUTES} minutes.</p>
 */
@Service("proportional")
public class ProportionalScheduler implements Scheduler {

    /** Default maximum work-session length in minutes. */
    public static final int DEFAULT_MAX_SESSION_MINUTES = 60;

    /** Sessions shorter than this are discarded as not worth scheduling. */
    static final int MIN_SESSION_MINUTES = 30;

    /**
     * Minimum gap (minutes) inserted after each placed session so back-to-back
     * sessions never run into each other.
     */
    private static final int MIN_GAP_AFTER_SESSION_MINUTES = 5;

    private final int maxSessionMinutes;

    /** Default constructor — 60-minute session cap. */
    public ProportionalScheduler() {
        this.maxSessionMinutes = DEFAULT_MAX_SESSION_MINUTES;
    }

    /**
     * Constructor that allows overriding the session cap (useful for tests).
     *
     * @param maxSessionMinutes maximum duration of a single session in minutes
     */
    public ProportionalScheduler(int maxSessionMinutes) {
        if (maxSessionMinutes < MIN_SESSION_MINUTES) {
            throw new IllegalArgumentException(
                    "maxSessionMinutes must be >= " + MIN_SESSION_MINUTES);
        }
        this.maxSessionMinutes = maxSessionMinutes;
    }

    // =========================================================================
    //  Scheduler contract
    // =========================================================================

    @Override
    public GeneratedSchedule generate(SchedulingRequest request) {
        validate(request);

        TimeRange range    = request.timeRange();
        double    pct      = request.percentageOfTimeToUse();
        List<TaskDTO> tasks = request.tasks().tasks();
        ZoneOffset offset  = range.from().getOffset();
        boolean recurrent  = request.recurrent();

        // ------------------------------------------------------------------
        // 1. Sort constraints once; reused for every day.
        // ------------------------------------------------------------------
        List<Interval> constraints = buildSortedConstraints(request.constraints(), offset);

        // ------------------------------------------------------------------
        // 2. Build day list; compute free slots per day.
        // ------------------------------------------------------------------
        List<LocalDate> days = buildDayList(range, offset);
        int numDays          = days.size();

        Map<LocalDate, List<Interval>> dayFreeSlots = new LinkedHashMap<>();
        long totalFreeMinutes = 0;

        for (LocalDate day : days) {
            List<Interval> freeSlots = computeFreeSlots(day, range, offset, constraints);
            dayFreeSlots.put(day, freeSlots);
            totalFreeMinutes += freeSlots.stream().mapToLong(Interval::durationMinutes).sum();
        }

        if (totalFreeMinutes == 0) {
            return new GeneratedSchedule(Collections.emptyList());
        }

        // ------------------------------------------------------------------
        // 3. Global task-time budget.
        //    Each day keeps (1 − pct) of its own free time truly free,
        //    so the free-time ratio is identical across all days.
        // ------------------------------------------------------------------
        long totalTaskMinutes = Math.round(totalFreeMinutes * pct);
        if (totalTaskMinutes == 0) {
            return new GeneratedSchedule(Collections.emptyList());
        }

        // ------------------------------------------------------------------
        // 4. Allocate minutes per task proportionally to priority.
        // ------------------------------------------------------------------
        double prioritySum = tasks.stream()
                .mapToDouble(t -> effectivePriority(t))
                .sum();

        // ------------------------------------------------------------------
        // 5. Build initial session queues per day.
        //    Each task's total allocation is spread equally across all days;
        //    each day's share is further split into sessions ≤ maxSessionMinutes.
        // ------------------------------------------------------------------
        Map<LocalDate, Deque<PendingSession>> dayQueues = new LinkedHashMap<>();
        for (LocalDate day : days) dayQueues.put(day, new ArrayDeque<>());

        for (TaskDTO task : tasks) {
            long taskTotal = Math.round(totalTaskMinutes * (effectivePriority(task) / prioritySum));
            if (taskTotal == 0) continue;

            // Spread evenly; extra minutes go to the earliest days first.
            long base  = taskTotal / numDays;
            long extra = taskTotal % numDays;

            for (int i = 0; i < numDays; i++) {
                long dayMinutes = base + (i < extra ? 1 : 0);
                enqueueSessionsForDay(dayQueues.get(days.get(i)), task, dayMinutes);
            }
        }

        // ------------------------------------------------------------------
        // 6. Day-by-day placement: interleave tasks + equal free-time gaps.
        // ------------------------------------------------------------------
        List<SchedulerEvent> events = new ArrayList<>();
        Deque<PendingSession> carryQueue = new ArrayDeque<>();

        for (int di = 0; di < numDays; di++) {
            LocalDate day = days.get(di);

            // Merge carried-over sessions with today's allocation.
            List<PendingSession> combined = new ArrayList<>();
            combined.addAll(carryQueue);
            combined.addAll(dayQueues.get(day));
            carryQueue.clear();

            // Interleave by task so different tasks alternate rather than cluster.
            List<PendingSession> interleaved = interleaveByTask(combined);

            // Place with equal gaps; unplaceable sessions go to carryQueue.
            List<SchedulerEvent> dayEvents =
                    placeWithEqualGaps(interleaved, dayFreeSlots.get(day), carryQueue, recurrent);
            events.addAll(dayEvents);
        }

        // Sessions still in the carry queue have no room left — they are dropped.
        return new GeneratedSchedule(events);
    }

    // =========================================================================
    //  Interleaving
    // =========================================================================

    /**
     * Round-robins sessions across tasks so the resulting order alternates between
     * different activities rather than clustering consecutive sessions of the same task.
     * <p>
     * Within each round the tasks are visited in descending priority order, ensuring
     * that higher-priority work always leads each alternation cycle.
     * <p>
     * Example with tasks A(p=10, 3 sessions), B(p=7, 2), C(p=3, 1):
     * {@code A, B, C, A, B, A}
     */
    private List<PendingSession> interleaveByTask(List<PendingSession> sessions) {
        // Group sessions by task title, preserving insertion order.
        Map<String, List<PendingSession>> byTask = new LinkedHashMap<>();
        for (PendingSession s : sessions) {
            byTask.computeIfAbsent(s.task().title(), k -> new ArrayList<>()).add(s);
        }

        // Sort task groups by descending priority so the highest-priority task
        // always leads each round.
        List<Map.Entry<String, List<PendingSession>>> groups = new ArrayList<>(byTask.entrySet());
        groups.sort((a, b) -> Double.compare(
                effectivePriority(b.getValue().get(0).task()),
                effectivePriority(a.getValue().get(0).task())));

        // Pointer per group.
        int[] ptrs = new int[groups.size()];
        List<PendingSession> result = new ArrayList<>(sessions.size());

        boolean anyLeft = true;
        while (anyLeft) {
            anyLeft = false;
            for (int g = 0; g < groups.size(); g++) {
                List<PendingSession> list = groups.get(g).getValue();
                if (ptrs[g] < list.size()) {
                    result.add(list.get(ptrs[g]++));
                    anyLeft = true;
                }
            }
        }
        return result;
    }

    // =========================================================================
    //  Equal-gap placement
    // =========================================================================

    /**
     * Places {@code sessions} into {@code freeSlots} such that the remaining free
     * time (slack) is divided into equal gaps distributed <em>before the first
     * session, between every pair of adjacent sessions, and after the last session</em>.
     * This makes the schedule look evenly spread rather than front-loaded.
     *
     * <p>Sessions are walked in the order provided (already interleaved by the
     * caller).  If a session does not fit in any remaining slot it is pushed to
     * {@code carryQueue} for the next day.</p>
     */
    private List<SchedulerEvent> placeWithEqualGaps(List<PendingSession> sessions,
                                                     List<Interval> freeSlots,
                                                     Deque<PendingSession> carryQueue,
                                                     boolean recurrent) {
        if (sessions.isEmpty() || freeSlots.isEmpty()) {
            carryQueue.addAll(sessions);
            return Collections.emptyList();
        }

        long totalFree = freeSlots.stream().mapToLong(Interval::durationMinutes).sum();

        // Determine which sessions actually fit on this day (respecting the
        // minimum-gap overhead so they are not squeezed together).
        List<PendingSession> fitting  = new ArrayList<>();
        List<PendingSession> overflow = new ArrayList<>();
        long consumed = 0;
        for (PendingSession s : sessions) {
            // Each session needs its own duration plus the minimum post-session gap.
            long needed = s.durationMinutes() + MIN_GAP_AFTER_SESSION_MINUTES;
            if (consumed + needed <= totalFree) {
                fitting.add(s);
                consumed += needed;
            } else {
                // Try a shorter partial session.
                long fittable = totalFree - consumed - MIN_GAP_AFTER_SESSION_MINUTES;
                if (fittable >= MIN_SESSION_MINUTES) {
                    fitting.add(new PendingSession(s.task(), fittable));
                    consumed += fittable + MIN_GAP_AFTER_SESSION_MINUTES;
                    long remainder = s.durationMinutes() - fittable;
                    if (remainder >= MIN_SESSION_MINUTES) {
                        overflow.add(new PendingSession(s.task(), remainder));
                    }
                } else {
                    overflow.add(s);
                }
            }
        }
        carryQueue.addAll(overflow);

        if (fitting.isEmpty()) return Collections.emptyList();

        // Compute equal gap: slack divided by (N + 1) spaces
        // (before first, between each pair, after last).
        long totalSessions = fitting.stream().mapToLong(PendingSession::durationMinutes).sum();
        long slack         = totalFree - totalSessions;
        int  n             = fitting.size();
        long gap           = Math.max(MIN_GAP_AFTER_SESSION_MINUTES, slack / (n + 1));

        // Walk through free slots with a cursor, advancing by `gap` before each session.
        FreeSlotCursor cursor = new FreeSlotCursor(new ArrayList<>(freeSlots));
        List<SchedulerEvent> events = new ArrayList<>();

        for (PendingSession session : fitting) {
            cursor.advance(gap);                          // equal gap before this session
            // Move cursor to first slot that has enough continuous room for this session.
            // MUST happen before capturing start — otherwise start and end can straddle a
            // constraint boundary and place an event on top of an existing calendar block.
            OffsetDateTime start = cursor.alignToFit(session.durationMinutes());
            if (start == null) {
                carryQueue.add(session);
                continue;
            }
            OffsetDateTime end = cursor.consume(session.durationMinutes());
            if (end == null) {
                carryQueue.add(session);
                continue;
            }
            events.add(buildEvent(session.task(), start, end, recurrent));
        }
        return events;
    }

    // =========================================================================
    //  Free-slot cursor
    // =========================================================================

    /**
     * Walks forward through a list of free time intervals, transparently skipping
     * over any gaps caused by constraints (existing calendar events).
     *
     * <ul>
     *   <li>{@link #advance(long)} — skip {@code minutes} of free time (used to
     *       insert the equal gap before a session).</li>
     *   <li>{@link #alignToFit(long)} — reposition to the start of the first free
     *       slot that has at least {@code minutes} of continuous room; MUST be called
     *       before capturing the event start time so that the cursor is never inside
     *       a slot that is too small (which would cause the event to span a
     *       constraint block).</li>
     *   <li>{@link #consume(long)} — occupy {@code minutes} starting at the current
     *       position (assumes {@link #alignToFit} was called first); returns the end
     *       {@link OffsetDateTime} or {@code null} if exhausted.</li>
     * </ul>
     */
    private static final class FreeSlotCursor {
        private final List<Interval> slots;
        private int idx;
        private OffsetDateTime pos;

        FreeSlotCursor(List<Interval> slots) {
            this.slots = slots;
            this.idx   = 0;
            this.pos   = slots.isEmpty() ? null : slots.get(0).start();
        }

        /** Move the cursor forward by {@code minutes} of free time. No-op if exhausted. */
        void advance(long minutes) {
            while (minutes > 0 && idx < slots.size()) {
                long avail = Duration.between(pos, slots.get(idx).end()).toMinutes();
                if (avail > minutes) {
                    pos = pos.plusMinutes(minutes);
                    return;
                }
                minutes -= avail;
                idx++;
                if (idx < slots.size()) pos = slots.get(idx).start();
            }
        }

        /**
         * Reposition the cursor to the earliest free-slot start that has at least
         * {@code minutes} of continuous room.  If the current slot already qualifies
         * the cursor is not moved.  Skips (without advancing through) slots that are
         * too small — this is intentional: those small pockets remain free time.
         *
         * @return the cursor position after alignment, or {@code null} if no fitting
         *         slot exists
         */
        OffsetDateTime alignToFit(long minutes) {
            while (idx < slots.size()) {
                long avail = Duration.between(pos, slots.get(idx).end()).toMinutes();
                if (avail >= minutes) return pos;
                // Slot too small — jump to the START of the next slot (do not consume time).
                idx++;
                if (idx < slots.size()) pos = slots.get(idx).start();
            }
            return null; // no slot large enough
        }

        /**
         * Consume {@code minutes} of free time starting at the current position.
         * Call {@link #alignToFit(long)} first to guarantee the current slot is
         * large enough; otherwise returns {@code null}.
         *
         * @return end {@link OffsetDateTime} of the consumed block, or {@code null}
         */
        OffsetDateTime consume(long minutes) {
            if (idx >= slots.size()) return null;
            long avail = Duration.between(pos, slots.get(idx).end()).toMinutes();
            if (avail < minutes) return null; // alignToFit was not called or slot is exhausted
            OffsetDateTime end = pos.plusMinutes(minutes);
            pos = end;
            return end;
        }
    }

    // =========================================================================
    //  Private helpers
    // =========================================================================

    /** Split a day's time allocation for one task into individual sessions. */
    private void enqueueSessionsForDay(Deque<PendingSession> queue,
                                       TaskDTO task,
                                       long dayMinutes) {
        while (dayMinutes >= MIN_SESSION_MINUTES) {
            long len = Math.min(dayMinutes, maxSessionMinutes);
            queue.add(new PendingSession(task, len));
            dayMinutes -= len;
        }
    }

    /**
     * Computes free (un-blocked) time intervals for one calendar day, clipped to
     * the overall scheduling range.
     *
     * @param day               the calendar day to analyse
     * @param range             overall scheduling time range (defines window clipping)
     * @param offset            UTC offset used to anchor midnight for {@code day}
     * @param sortedConstraints all constraints sorted by start time
     * @return mutable list of free intervals, each ≥ {@value #MIN_SESSION_MINUTES} min
     */
    private List<Interval> computeFreeSlots(LocalDate day,
                                             TimeRange range,
                                             ZoneOffset offset,
                                             List<Interval> sortedConstraints) {
        // Day window anchored to the correct UTC offset.
        OffsetDateTime dayStart = day.atStartOfDay().atOffset(offset);
        OffsetDateTime dayEnd   = day.plusDays(1).atStartOfDay().atOffset(offset);

        // Clip to the overall scheduling range.
        OffsetDateTime winStart = dayStart.isBefore(range.from()) ? range.from() : dayStart;
        OffsetDateTime winEnd   = dayEnd.isAfter(range.to())       ? range.to()   : dayEnd;

        if (!winStart.isBefore(winEnd)) return Collections.emptyList();

        // Start with the full window and punch out every constraint.
        List<Interval> free = new ArrayList<>();
        free.add(new Interval(winStart, winEnd));

        for (Interval c : sortedConstraints) {
            if (!c.start().isBefore(winEnd)) break;    // all remaining constraints are after window
            if (!c.end().isAfter(winStart))   continue; // constraint entirely before window

            List<Interval> next = new ArrayList<>();
            for (Interval slot : free) {
                // Left remnant: [slot.start, min(c.start, slot.end)]
                if (slot.start().isBefore(c.start())) {
                    OffsetDateTime leftEnd = c.start().isBefore(slot.end()) ? c.start() : slot.end();
                    if (slot.start().isBefore(leftEnd)) {
                        next.add(new Interval(slot.start(), leftEnd));
                    }
                }
                // Right remnant: [max(c.end, slot.start), slot.end]
                if (slot.end().isAfter(c.end())) {
                    OffsetDateTime rightStart = c.end().isAfter(slot.start()) ? c.end() : slot.start();
                    if (rightStart.isBefore(slot.end())) {
                        next.add(new Interval(rightStart, slot.end()));
                    }
                }
            }
            free = next;
        }

        // Discard slots too short for even a minimum session.
        free.removeIf(s -> s.durationMinutes() < MIN_SESSION_MINUTES);
        return free;
    }

    private List<LocalDate> buildDayList(TimeRange range, ZoneOffset offset) {
        List<LocalDate> days   = new ArrayList<>();
        LocalDate cursor       = range.from().withOffsetSameInstant(offset).toLocalDate();
        LocalDate last         = range.to().withOffsetSameInstant(offset).toLocalDate();
        while (!cursor.isAfter(last)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    private List<Interval> buildSortedConstraints(List<ScheduleConstraint> raw,
                                                   ZoneOffset offset) {
        return raw.stream()
                .filter(c -> c.start() != null && c.end() != null)
                .filter(c -> c.start().isBefore(c.end()))
                .map(c -> new Interval(
                        c.start().withOffsetSameInstant(offset),
                        c.end().withOffsetSameInstant(offset)))
                .sorted(Comparator.comparing(Interval::start))
                .toList();
    }

    private SchedulerEvent buildEvent(TaskDTO task, OffsetDateTime start, OffsetDateTime end,
                                       boolean recurrent) {
        String desc = "Priority: " + (task.priority() == null ? "\u2014" : task.priority().intValue())
                + " | Scheduled by Proportional Scheduler";
        List<String> rrule = null;
        if (recurrent) {
            // Weekly recurrence on the same day of week as the placed event.
            String byday = dayOfWeekToRrule(start.getDayOfWeek());
            rrule = List.of("RRULE:FREQ=WEEKLY;BYDAY=" + byday);
        }
        return new SchedulerEvent(
                task.title(),
                desc,
                colorForPriority(task.priority()),
                start,
                end,
                null,
                rrule
        );
    }

    /** Maps a {@link DayOfWeek} to its two-letter RFC 5545 representation. */
    private static String dayOfWeekToRrule(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> "MO";
            case TUESDAY   -> "TU";
            case WEDNESDAY -> "WE";
            case THURSDAY  -> "TH";
            case FRIDAY    -> "FR";
            case SATURDAY  -> "SA";
            case SUNDAY    -> "SU";
        };
    }

    private static double effectivePriority(TaskDTO task) {
        return task.priority() == null ? 1.0 : Math.max(0.1, task.priority());
    }

    /**
     * Maps a priority score (1–10) to a distinct Google Calendar color so that
     * the event color directly communicates the task's importance at a glance.
     *
     * <pre>
     *  1  → GRAPHITE   (lowest)
     *  2  → LAVENDER
     *  3  → SAGE
     *  4  → BASIL
     *  5  → PEACOCK    (medium)
     *  6  → BLUEBERRY
     *  7  → BANANA
     *  8  → TANGERINE
     *  9  → FLAMINGO
     *  10 → TOMATO     (critical)
     * </pre>
     */
    static CalendarColor colorForPriority(Double priority) {
        if (priority == null) return CalendarColor.GRAPHITE;
        return switch (Math.max(1, Math.min(10, (int) Math.round(priority)))) {
            case 1  -> CalendarColor.GRAPHITE;
            case 2  -> CalendarColor.LAVENDER;
            case 3  -> CalendarColor.SAGE;
            case 4  -> CalendarColor.BASIL;
            case 5  -> CalendarColor.PEACOCK;
            case 6  -> CalendarColor.BLUEBERRY;
            case 7  -> CalendarColor.BANANA;
            case 8  -> CalendarColor.TANGERINE;
            case 9  -> CalendarColor.FLAMINGO;
            default -> CalendarColor.TOMATO;
        };
    }

    private void validate(SchedulingRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Request cannot be null");
        if (request.tasks() == null
                || request.tasks().tasks() == null
                || request.tasks().tasks().isEmpty())
            throw new IllegalArgumentException("Task list cannot be empty");
        if (request.constraints() == null)
            throw new IllegalArgumentException("Constraints cannot be null");
        if (request.percentageOfTimeToUse() <= 0 || request.percentageOfTimeToUse() > 1)
            throw new IllegalArgumentException("percentageOfTimeToUse must be in (0, 1]");
        if (request.timeRange() == null
                || request.timeRange().from() == null
                || request.timeRange().to() == null)
            throw new IllegalArgumentException("Time range must be fully defined");
        if (!request.timeRange().from().isBefore(request.timeRange().to()))
            throw new IllegalArgumentException("timeRange.from must be before timeRange.to");
    }

    // =========================================================================
    //  Private value types
    // =========================================================================

    private record Interval(OffsetDateTime start, OffsetDateTime end) {
        long durationMinutes() {
            return java.time.Duration.between(start, end).toMinutes();
        }
    }

    private record PendingSession(TaskDTO task, long durationMinutes) {}
}
