package com.ai.scheduler.schedulers;

import com.ai.scheduler.dto.calendar.CalendarColor;
import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.calendar.scheduler.SchedulerEvent;
import com.ai.scheduler.dto.calendar.scheduler.SchedulingRequest;
import com.ai.scheduler.dto.llm.TaskDTO;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Physics-inspired scheduler — models tasks as charged particles on a 1-D time axis.
 *
 * <h3>Force System</h3>
 * <ol>
 *   <li><b>Inter-task repulsion / attraction</b> — tasks with HIGH cognitive load repel each
 *       other strongly (you don't want two demanding sessions back-to-back). Tasks with LOW
 *       cognitive load attract each other, allowing light work to cluster.
 *       The net force coefficient for a pair (i, j) is:<br>
 *       {@code k_net = cogLoad_i * cogLoad_j / 100  −  (10-cogLoad_i) * (10-cogLoad_j) / 100}
 *       <ul>
 *         <li>Both high (8,8): k ≈ +0.60  → strong repulsion</li>
 *         <li>Both low  (2,2): k ≈ −0.60  → attractive</li>
 *         <li>One each  (8,2): k ≈  0.00  → neutral</li>
 *       </ul>
 *   </li>
 *   <li><b>Calendar-event repulsion</b> — existing Google Calendar events push every task
 *       particle away with a strong 1/d² force (hard constraint enforcement).</li>
 *   <li><b>Time-of-day repulsion</b> — smooth Gaussian potential wells centred on bad time
 *       windows. The negative gradient of each Gaussian naturally pushes particles away:
 *       <ul>
 *         <li>Night  (~02:00, σ=3 h) — very strong</li>
 *         <li>Evening (~23:00, σ=2 h) — strong</li>
 *         <li>Lunch  (~12:30, σ=0.5 h) — moderate</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * After the simulation converges a greedy repair pass resolves any remaining overlaps
 * and ensures the minimum gap between sessions.
 */
@Service("physics")
public class PhysicsScheduler implements Scheduler {

    // ── Simulation tuning constants ──────────────────────────────────────────

    /** Base repulsion strength between any pair of task particles. */
    private static final double INTER_TASK_REPULSION = 4_000.0;
    /** Extra attraction bonus for low-cognitive-load pairs. */
    private static final double LOW_LOAD_ATTRACTION  = 2_000.0;
    /** Repulsion from hard constraint blocks (calendar events). */
    private static final double CONSTRAINT_REPULSION = 12_000.0;
    /** Amplitude of the night-time Gaussian repulsion wells. */
    private static final double NIGHT_REPULSION      = 10_000.0;
    /** Amplitude of the lunch-time Gaussian repulsion well. */
    private static final double LUNCH_REPULSION      = 2_500.0;
    /** Softening length (minutes) — prevents 1/d² singularities when tasks touch. */
    private static final double SOFTENING            = 8.0;
    /** Velocity damping per simulation step — dissipates kinetic energy. */
    private static final double DAMPING              = 0.82;
    /** Simulation time step (minutes). */
    private static final double DT                   = 1.0;
    /** Total number of simulation steps. */
    private static final int    ITERATIONS           = 700;
    /** Minimum enforced gap between any two sessions after repair (minutes). */
    private static final double MIN_GAP              = 15.0;
    /** Minutes in a day — used for cyclic time-of-day computation. */
    private static final double MINUTES_PER_DAY      = 1_440.0;

    // ── Entry point ──────────────────────────────────────────────────────────

    @Override
    public GeneratedSchedule generate(SchedulingRequest request) {
        validate(request);

        OffsetDateTime rangeStart = request.timeRange().from();
        OffsetDateTime rangeEnd   = request.timeRange().to();
        double totalMin = minutesBetween(rangeStart, rangeEnd);

        List<TaskDTO>            tasks       = request.tasks().tasks();
        List<ScheduleConstraint> constraints = request.constraints();

        // Build constraint blocks in [minutes-from-start] coordinates
        List<double[]> constraintBlocks = toConstraintBlocks(constraints, rangeStart, totalMin);

        // Compute per-task session durations (proportional by priority, capped by cognitive load)
        double[] durations = computeDurations(tasks, totalMin, constraintBlocks,
                request.percentageOfTimeToUse());

        // Seed positions evenly across free time
        List<double[]> freeIntervals = buildFreeIntervals(0, totalMin, constraintBlocks);
        double[]       positions     = seedPositions(tasks.size(), durations, freeIntervals, totalMin);
        double[]       velocities    = new double[tasks.size()];

        // Run physics simulation
        runSimulation(positions, velocities, durations, tasks, constraintBlocks, totalMin, rangeStart);

        // Post-simulation greedy repair (resolves remaining collisions / gaps)
        repairPositions(positions, durations, constraintBlocks, totalMin);

        return new GeneratedSchedule(buildEvents(tasks, positions, durations, rangeStart, request));
    }

    // ── 1. Duration allocation ───────────────────────────────────────────────

    private double[] computeDurations(List<TaskDTO> tasks, double totalMin,
                                      List<double[]> constraintBlocks, double pct) {
        double busyMin    = constraintBlocks.stream().mapToDouble(b -> b[1] - b[0]).sum();
        double available  = Math.max(0, (totalMin - busyMin) * pct);
        double sumPriority = tasks.stream().mapToDouble(t -> safePriority(t)).sum();
        if (sumPriority == 0) sumPriority = 1;

        double[] durations = new double[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            TaskDTO t = tasks.get(i);
            double share = available * (safePriority(t) / sumPriority);
            double cap   = maxSessionMinutes(safeCogLoad(t));
            durations[i] = Math.max(15.0, Math.min(share, cap));
        }
        return durations;
    }

    /**
     * Cognitive load → max single-session length.
     * High load ⇒ shorter bursts; low load ⇒ longer uninterrupted sessions.
     *
     * <pre>cogLoad  1 → 120 min,  cogLoad 10 → 30 min  (linear)</pre>
     */
    private static double maxSessionMinutes(double cogLoad) {
        return 120.0 - (cogLoad - 1.0) * (90.0 / 9.0);
    }

    // ── 2. Constraint blocks ─────────────────────────────────────────────────

    private static List<double[]> toConstraintBlocks(List<ScheduleConstraint> constraints,
                                                     OffsetDateTime rangeStart, double totalMin) {
        List<double[]> blocks = new ArrayList<>();
        for (ScheduleConstraint c : constraints) {
            double s = Math.max(0,        minutesBetween(rangeStart, c.start()));
            double e = Math.min(totalMin, minutesBetween(rangeStart, c.end()));
            if (e > s) blocks.add(new double[]{s, e});
        }
        blocks.sort(Comparator.comparingDouble(b -> b[0]));
        return blocks;
    }

    // ── 3. Free interval computation ─────────────────────────────────────────

    private static List<double[]> buildFreeIntervals(double start, double end,
                                                     List<double[]> blocks) {
        List<double[]> free   = new ArrayList<>();
        double         cursor = start;
        for (double[] b : blocks) {
            if (b[0] > cursor) free.add(new double[]{cursor, b[0]});
            cursor = Math.max(cursor, b[1]);
        }
        if (cursor < end) free.add(new double[]{cursor, end});
        return free;
    }

    // ── 4. Position seeding ──────────────────────────────────────────────────

    private static double[] seedPositions(int n, double[] durations,
                                          List<double[]> freeIntervals, double totalMin) {
        double[] positions = new double[n];
        if (freeIntervals.isEmpty()) {
            for (int i = 0; i < n; i++) positions[i] = totalMin * i / (n + 1);
            return positions;
        }
        double totalFree = freeIntervals.stream().mapToDouble(iv -> iv[1] - iv[0]).sum();
        for (int i = 0; i < n; i++) {
            double target  = totalFree * (i + 1.0) / (n + 1.0);
            double cum     = 0;
            double chosen  = freeIntervals.get(0)[0];
            for (double[] iv : freeIntervals) {
                double len = iv[1] - iv[0];
                if (cum + len >= target) {
                    chosen = iv[0] + (target - cum);
                    break;
                }
                cum   += len;
                chosen = iv[1];
            }
            positions[i] = Math.max(0, Math.min(totalMin - durations[i], chosen));
        }
        return positions;
    }

    // ── 5. Physics simulation ────────────────────────────────────────────────

    private void runSimulation(double[] positions, double[] velocities, double[] durations,
                               List<TaskDTO> tasks, List<double[]> constraintBlocks,
                               double totalMin, OffsetDateTime rangeStart) {
        int      n      = positions.length;
        double[] forces = new double[n];

        for (int iter = 0; iter < ITERATIONS; iter++) {
            Arrays.fill(forces, 0.0);

            // ── Inter-task forces ────────────────────────────────────────────
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    applyInterTaskForce(i, j, positions, durations, tasks, forces);
                }
            }

            // ── Calendar event repulsion ─────────────────────────────────────
            for (int i = 0; i < n; i++) {
                forces[i] += constraintRepulsion(positions[i], durations[i], constraintBlocks);
            }

            // ── Time-of-day forces ────────────────────────────────────────────
            for (int i = 0; i < n; i++) {
                forces[i] += timeOfDayForce(positions[i], durations[i], rangeStart);
            }

            // ── Soft range boundary ───────────────────────────────────────────
            for (int i = 0; i < n; i++) {
                if (positions[i] < 0) {
                    forces[i] += INTER_TASK_REPULSION * (-positions[i]) / SOFTENING;
                }
                double tail = positions[i] + durations[i] - totalMin;
                if (tail > 0) {
                    forces[i] -= INTER_TASK_REPULSION * tail / SOFTENING;
                }
            }

            // ── Euler integration with damping ────────────────────────────────
            for (int i = 0; i < n; i++) {
                velocities[i] = (velocities[i] + forces[i] * DT) * DAMPING;
                positions[i]  = Math.max(0, Math.min(totalMin - durations[i],
                        positions[i] + velocities[i] * DT));
            }
        }
    }

    // ── Force functions ──────────────────────────────────────────────────────

    /**
     * Computes and accumulates the inter-task physics force between particles i and j.
     *
     * <p>The net force coefficient {@code k} determines whether the pair repels or attracts:
     * <pre>k = cogLoad_i * cogLoad_j / 100  −  (10-cogLoad_i) * (10-cogLoad_j) / 100</pre>
     *
     * <ul>
     *   <li>k &gt; 0  → net repulsion (both high-load)</li>
     *   <li>k &lt; 0  → net attraction (both low-load)</li>
     *   <li>k ≈ 0   → neutral (mixed-load pair)</li>
     * </ul>
     */
    private void applyInterTaskForce(int i, int j, double[] positions, double[] durations,
                                     List<TaskDTO> tasks, double[] forces) {
        double ci = positions[i] + durations[i] / 2.0;  // centre of particle i
        double cj = positions[j] + durations[j] / 2.0;  // centre of particle j
        double dist  = Math.abs(ci - cj);
        double dSoft = dist + SOFTENING;

        double cogI = safeCogLoad(tasks.get(i));
        double cogJ = safeCogLoad(tasks.get(j));

        // Repulsion coefficient (range 0.01 → 1.0, high when both high-load)
        double repCoeff  = (cogI * cogJ) / 100.0;
        // Attraction coefficient (range 0 → 0.81, high when both low-load)
        double attrCoeff = ((10 - cogI) * (10 - cogJ)) / 100.0;

        double repulsionMag  = INTER_TASK_REPULSION * repCoeff  / (dSoft * dSoft);
        double attractionMag = LOW_LOAD_ATTRACTION  * attrCoeff / dSoft;  // softer 1/d spring

        double netMag   = repulsionMag - attractionMag;
        double direction = (ci > cj) ? 1.0 : -1.0;   // direction from j → i
        if (dist == 0) direction = (i % 2 == 0) ? 1.0 : -1.0;  // tie-break

        forces[i] += direction * netMag;
        forces[j] -= direction * netMag;
    }

    /**
     * Total repulsion force on a task from all hard calendar-event constraint blocks.
     * Each constraint pushes the task particle away with a 1/d² force.
     */
    private static double constraintRepulsion(double pos, double dur,
                                              List<double[]> constraintBlocks) {
        double force      = 0;
        double taskCentre = pos + dur / 2.0;
        for (double[] block : constraintBlocks) {
            double blockCentre = (block[0] + block[1]) / 2.0;
            double dist  = Math.abs(taskCentre - blockCentre);
            double dSoft = dist + SOFTENING;
            double dir   = (taskCentre >= blockCentre) ? 1.0 : -1.0;
            force += dir * CONSTRAINT_REPULSION / (dSoft * dSoft);
        }
        return force;
    }

    /**
     * Time-of-day repulsion force on a task particle.
     *
     * <p>Implemented as the negative gradient (F = −dV/dx) of a sum of Gaussian
     * potential wells placed at "bad" times of day. This naturally pushes tasks
     * away from the peak of each Gaussian, producing a smooth, differentiable
     * repulsion that tapers off gracefully at the edges of each bad zone.
     *
     * <h4>Bad-time Gaussians</h4>
     * <table>
     *   <tr><th>Zone</th>         <th>Centre (h)</th> <th>σ (h)</th> <th>Amplitude</th></tr>
     *   <tr><td>Deep night</td>   <td>02:00</td>      <td>3.0</td>  <td>NIGHT (full)</td></tr>
     *   <tr><td>Late evening</td> <td>23:00</td>      <td>2.0</td>  <td>NIGHT × 0.65</td></tr>
     *   <tr><td>Lunch</td>        <td>12:30</td>      <td>0.5</td>  <td>LUNCH (full)</td></tr>
     * </table>
     *
     * <p>The force is computed in hour-space then divided by 60 to convert to minute-space
     * (consistent with position units in the simulation).
     */
    private static double timeOfDayForce(double posStart, double duration,
                                         OffsetDateTime rangeStart) {
        // Absolute position of task centre in minutes from Unix-like epoch reference
        long   startEpochMin = rangeStart.toEpochSecond() / 60L;
        double absCentreMin  = startEpochMin + posStart + duration / 2.0;

        // Map to hour-of-day [0, 24)
        double minOfDay  = absCentreMin % MINUTES_PER_DAY;
        if (minOfDay < 0) minOfDay += MINUTES_PER_DAY;
        double hourOfDay = minOfDay / 60.0;

        double forceHours = 0;

        // ── Night zone: deep night (02:00, σ=3 h) ───────────────────────────
        forceHours += gaussianGradient(hourOfDay,  2.0, 3.0, NIGHT_REPULSION);
        // ── Night zone: late evening (23:00, σ=2 h) ─────────────────────────
        forceHours += gaussianGradient(hourOfDay, 23.0, 2.0, NIGHT_REPULSION * 0.65);
        // Handle wrap-around: tasks just past midnight also feel the 23h Gaussian
        if (hourOfDay < 4.0) {
            forceHours += gaussianGradient(hourOfDay - 24.0, 23.0, 2.0, NIGHT_REPULSION * 0.65);
        }
        // ── Lunch zone (12:30, σ=0.5 h) ─────────────────────────────────────
        forceHours += gaussianGradient(hourOfDay, 12.5, 0.5, LUNCH_REPULSION);

        // Convert hours → minutes (position units used in simulation)
        return forceHours / 60.0;
    }

    /**
     * Returns the negative gradient of a Gaussian potential at position {@code x}:
     * <pre>V(x) = A · exp(−(x−μ)² / (2σ²))</pre>
     * <pre>F = −dV/dx = A · (x−μ)/σ² · exp(−(x−μ)²/(2σ²))</pre>
     *
     * A particle to the LEFT of μ gets pushed further left (negative force);
     * a particle to the RIGHT gets pushed further right (positive force).
     * This constitutes a smooth repulsion away from the centre μ.
     */
    private static double gaussianGradient(double x, double mu, double sigma, double amplitude) {
        double diff = x - mu;
        return amplitude * (diff / (sigma * sigma))
                * Math.exp(-(diff * diff) / (2.0 * sigma * sigma));
    }

    // ── 6. Post-simulation repair ────────────────────────────────────────────

    /**
     * Greedy repair pass: sorts tasks by final position, then shifts each one to
     * the earliest valid slot that satisfies the minimum gap from all constraint
     * blocks and previously placed tasks.
     */
    private static void repairPositions(double[] positions, double[] durations,
                                        List<double[]> constraintBlocks, double totalMin) {
        int       n   = positions.length;
        Integer[] idx = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingDouble(i -> positions[i]))
                .toArray(Integer[]::new);

        // Start with constraint blocks as occupied zones (with gap padding)
        List<double[]> occupied = new ArrayList<>();
        for (double[] b : constraintBlocks) {
            occupied.add(new double[]{b[0] - MIN_GAP, b[1] + MIN_GAP});
        }

        for (int k = 0; k < n; k++) {
            int    i      = idx[k];
            double newPos = findFirstFit(positions[i], durations[i], occupied, totalMin);
            positions[i]  = newPos;
            // Register this session as occupied (with gap on both sides) for subsequent tasks
            occupied.add(new double[]{newPos - MIN_GAP, newPos + durations[i] + MIN_GAP});
            occupied.sort(Comparator.comparingDouble(b -> b[0]));
        }
    }

    /**
     * Finds the earliest start position ≥ {@code hint} where a block of length
     * {@code dur} fits without overlapping any occupied interval.
     */
    private static double findFirstFit(double hint, double dur,
                                       List<double[]> occupied, double totalMin) {
        double pos = Math.max(0, hint);
        boolean moved;
        do {
            moved = false;
            for (double[] block : occupied) {
                if (pos < block[1] && pos + dur > block[0]) {
                    pos   = block[1];      // jump past the blocking interval
                    moved = true;
                }
            }
        } while (moved);
        return Math.min(pos, Math.max(0, totalMin - dur));
    }

    // ── 7. Event assembly ────────────────────────────────────────────────────

    private static List<SchedulerEvent> buildEvents(List<TaskDTO> tasks, double[] positions,
                                                    double[] durations, OffsetDateTime rangeStart,
                                                    SchedulingRequest request) {
        List<SchedulerEvent> events = new ArrayList<>();
        String tz = rangeStart.getOffset().getId();

        for (int i = 0; i < tasks.size(); i++) {
            TaskDTO        task  = tasks.get(i);
            OffsetDateTime start = rangeStart.plusMinutes(Math.round(positions[i]));
            OffsetDateTime end   = rangeStart.plusMinutes(Math.round(positions[i] + durations[i]));
            CalendarColor  color = colorForCognitiveLoad(safeCogLoad(task));

            List<String> rrule = List.of();
            if (request.recurrent()) {
                String byday = dayOfWeekToRrule(start.getDayOfWeek());
                rrule = List.of("RRULE:FREQ=WEEKLY;BYDAY=" + byday);
            }

            events.add(new SchedulerEvent(
                    task.title(),
                    "Physics-scheduled | Priority: %.0f | Cognitive Load: %.0f"
                            .formatted(safePriority(task), safeCogLoad(task)),
                    color,
                    start,
                    end,
                    tz,
                    rrule
            ));
        }
        return events;
    }

    // ── Color mapping ────────────────────────────────────────────────────────

    /**
     * Maps cognitive load (1–10) to a Google Calendar color.
     *
     * <pre>
     *  1–2  → SAGE       (calm green — very light work)
     *  3–4  → PEACOCK    (teal — light work)
     *  5–6  → BLUEBERRY  (blue — moderate focus)
     *  7–8  → BANANA     (yellow — high effort)
     *  9–10 → TOMATO     (red — maximum cognitive demand)
     * </pre>
     */
    private static CalendarColor colorForCognitiveLoad(double cogLoad) {
        if (cogLoad <= 2.0) return CalendarColor.SAGE;
        if (cogLoad <= 4.0) return CalendarColor.PEACOCK;
        if (cogLoad <= 6.0) return CalendarColor.BLUEBERRY;
        if (cogLoad <= 8.0) return CalendarColor.BANANA;
        return CalendarColor.TOMATO;
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private static void validate(SchedulingRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Request is null");
        if (request.tasks() == null || request.tasks().tasks() == null
                || request.tasks().tasks().isEmpty())
            throw new IllegalArgumentException("Task list is empty");
        if (request.timeRange() == null || request.timeRange().from() == null
                || request.timeRange().to() == null)
            throw new IllegalArgumentException("Time range is not defined");
        if (request.percentageOfTimeToUse() <= 0 || request.percentageOfTimeToUse() > 1)
            throw new IllegalArgumentException("percentageOfTimeToUse must be in (0, 1]");
    }

    private static double minutesBetween(OffsetDateTime a, OffsetDateTime b) {
        return java.time.Duration.between(a, b).toSeconds() / 60.0;
    }

    /** Null-safe priority accessor — defaults to 5.0. */
    private static double safePriority(TaskDTO t) {
        return (t.priority() != null) ? Math.max(0.1, t.priority()) : 5.0;
    }

    /** Null-safe cognitive-load accessor — defaults to 5.0 (neutral). */
    private static double safeCogLoad(TaskDTO t) {
        Double cl = t.cognitiveLoad();
        return (cl != null) ? Math.min(10.0, Math.max(1.0, cl)) : 5.0;
    }

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
}
