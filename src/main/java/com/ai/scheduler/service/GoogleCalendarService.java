package com.ai.scheduler.service;

import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventRequest;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarService {

    private static final String APPLICATION_NAME = "AI Scheduler";
    private static final String PRIMARY_CALENDAR = "primary";

    /**
     * Builds a Google Calendar client using the provided OAuth2 access token.
     * OAuth flow is handled externally; this service only needs the access token.
     */
    private Calendar buildClient(String accessToken) throws GeneralSecurityException, IOException {
        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Creates a new event in the user's primary Google Calendar.
     */
    public CalendarEventResponse createEvent(String accessToken, CalendarEventRequest request)
            throws IOException, GeneralSecurityException {
        Calendar service = buildClient(accessToken);

        Event event = new Event()
                .setSummary(request.title())
                .setDescription(request.description());

        if (request.color() != null) {
            event.setColorId(request.color().getColorId());
        }

        String timeZone = request.timeZone() != null ? request.timeZone() : "UTC";

        EventDateTime start = new EventDateTime()
                .setDateTime(toGoogleDateTime(request.startDateTime()))
                .setTimeZone(timeZone);
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(toGoogleDateTime(request.endDateTime()))
                .setTimeZone(timeZone);
        event.setEnd(end);

        Event created = service.events().insert(PRIMARY_CALENDAR, event).execute();
        return toResponse(created);
    }

    /**
     * Returns all events occurring on the given day.
     */
    public List<CalendarEventResponse> getEventsForDay(String accessToken, LocalDate date, String timeZone)
            throws IOException, GeneralSecurityException {
        return getEventsForDays(accessToken, List.of(date), timeZone);
    }

    /**
     * Returns all events occurring on any of the given days, sorted by start time.
     * Fetches in a single API call spanning the min-to-max date range, then
     * filters to only the requested dates.
     */
    public List<CalendarEventResponse> getEventsForDays(String accessToken, List<LocalDate> dates, String timeZone)
            throws IOException, GeneralSecurityException {
        if (dates == null || dates.isEmpty()) {
            return List.of();
        }

        Calendar service = buildClient(accessToken);
        ZoneId zoneId = ZoneId.of(timeZone != null ? timeZone : "UTC");

        LocalDate minDate = dates.stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = dates.stream().max(Comparator.naturalOrder()).orElseThrow();

        DateTime timeMin = new DateTime(minDate.atStartOfDay(zoneId).toInstant().toEpochMilli());
        DateTime timeMax = new DateTime(maxDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli());

        Events eventsResult = service.events().list(PRIMARY_CALENDAR)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setTimeZone(timeZone != null ? timeZone : "UTC")
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        Set<LocalDate> dateSet = new HashSet<>(dates);
        List<Event> items = eventsResult.getItems();
        if (items == null) {
            return List.of();
        }

        List<CalendarEventResponse> result = new ArrayList<>();
        for (Event event : items) {
            LocalDate eventDate = extractEventDate(event, zoneId);
            if (eventDate != null && dateSet.contains(eventDate)) {
                result.add(toResponse(event));
            }
        }
        return result;
    }

    // --- helpers ---

    private DateTime toGoogleDateTime(OffsetDateTime offsetDateTime) {
        return new DateTime(offsetDateTime.toInstant().toEpochMilli());
    }

    private LocalDate extractEventDate(Event event, ZoneId zoneId) {
        EventDateTime start = event.getStart();
        if (start == null) return null;

        if (start.getDate() != null) {
            // All-day event: date is in yyyy-MM-dd format
            String[] parts = start.getDate().toStringRfc3339().split("-");
            return LocalDate.of(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }
        if (start.getDateTime() != null) {
            return OffsetDateTime
                    .ofInstant(
                            java.time.Instant.ofEpochMilli(start.getDateTime().getValue()),
                            zoneId)
                    .toLocalDate();
        }
        return null;
    }

    private CalendarEventResponse toResponse(Event event) {
        OffsetDateTime start = parseEventDateTime(event.getStart());
        OffsetDateTime end = parseEventDateTime(event.getEnd());
        String timeZone = event.getStart() != null ? event.getStart().getTimeZone() : null;

        return new CalendarEventResponse(
                event.getId(),
                event.getSummary(),
                event.getDescription(),
                event.getColorId(),
                start,
                end,
                timeZone,
                event.getHtmlLink());
    }

    private OffsetDateTime parseEventDateTime(EventDateTime eventDateTime) {
        if (eventDateTime == null) return null;

        if (eventDateTime.getDateTime() != null) {
            return OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(eventDateTime.getDateTime().getValue()),
                    ZoneOffset.UTC);
        }
        if (eventDateTime.getDate() != null) {
            // All-day: return start of day in UTC
            String raw = eventDateTime.getDate().toStringRfc3339();
            String[] parts = raw.split("-");
            return LocalDate.of(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]))
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}

