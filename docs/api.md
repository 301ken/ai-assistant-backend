# API Reference

REST API for the **AI Scheduler** backend.

- **Base URL (local):** `http://localhost:8085`
- **Content type:** `application/json` (unless noted)
- **Auth:** all endpoints require `Authorization: Bearer <jwt>` **except** those under
  `/api/auth/**` and the Google OAuth callback.
- **Dates/times:** `LocalDate` = `YYYY-MM-DD`, `LocalTime` = `HH:mm:ss`,
  `OffsetDateTime` = ISO-8601 with offset (e.g. `2026-05-30T09:00:00+00:00`).

### Error format

All errors share a consistent body produced by `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-05-30T12:00:00",
  "status": 400,
  "error": "Email already registered"
}
```

Validation failures include a `details` map:

```json
{
  "timestamp": "2026-05-30T12:00:00",
  "status": 400,
  "error": "Validation failed",
  "details": { "email": "must be a well-formed email address" }
}
```

| Situation | Status |
|-----------|:------:|
| Bean validation / `ApiException` / bad request | 400 |
| Invalid credentials | 401 |
| Resource not found | 404 |
| Unhandled error | 500 |

---

## Authentication — `/api/auth`

### Register

`POST /api/auth/register` · **public**

```json
{ "name": "Ada Lovelace", "email": "ada@example.com", "password": "min8chars" }
```

Validation: `name` not blank · `email` valid & not blank · `password` 8–100 chars.

**201 Created**

```json
{
  "id": 1,
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "accountActivated": true,
  "message": "Registration successful. Please check your email to verify your account."
}
```

A verification email is sent. SMTP failures are logged, not surfaced (registration still succeeds).

### Verify email

`GET /api/auth/verify-email?token=<uuid>` · **public**

**200 OK** → `{ "message": "Email verified successfully" }`
Invalid / used / expired token → **400**.

### Login

`POST /api/auth/login` · **public**

```json
{ "email": "ada@example.com", "password": "min8chars" }
```

**200 OK**

```json
{
  "token": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "userId": 1,
  "name": "Ada Lovelace",
  "email": "ada@example.com"
}
```

Unknown email, unverified account, or wrong password → **400/401**.

### Logout

`POST /api/auth/logout` · header `Authorization: Bearer <jwt>`

Revokes the presented token (added to the blacklist until it naturally expires).

**200 OK** → `{ "message": "Logged out successfully" }`
Missing or non-`Bearer` header → **400**.

---

## Users — `/api/users`

### Get current user

`GET /api/users/me`

**200 OK**

```json
{
  "id": 1,
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "accountActivated": true,
  "createdAt": "2026-05-01T10:15:30"
}
```

### Update current user

`PUT /api/users/me`

```json
{ "name": "Ada L." }
```

`name` must not be blank. **200 OK** → updated `UserResponse`.

### Delete current user

`DELETE /api/users/me`

Deletes the user and all their activities. **204 No Content**.

---

## Activities — `/api/activities`

An **activity** is a tracked time session, optionally linked to a Google Calendar event.

### List

`GET /api/activities`

Optional query params:

| Param | Type | Effect |
|-------|------|--------|
| `activityType` | `FOCUS \| MEETING \| BREAK \| ROUTINE` | filter by type |
| `date` | `YYYY-MM-DD` | filter by date |

**200 OK** → array of `ActivityResponse`.

### Create

`POST /api/activities`

```json
{
  "activityType": "FOCUS",
  "activityDescription": "Deep work on physics report",
  "date": "2026-05-30",
  "startTime": "09:00:00",
  "endTime": "10:30:00",
  "calendarEventId": "abc123",
  "calendarEventTitle": "Physics report"
}
```

Required: `activityType`, `activityDescription` (not blank), `date`, `startTime`, `endTime`.
`calendarEventId` / `calendarEventTitle` are optional (used for planned-vs-actual stats).

**201 Created** → `ActivityResponse`:

```json
{
  "id": 10,
  "userId": 1,
  "activityType": "FOCUS",
  "activityDescription": "Deep work on physics report",
  "date": "2026-05-30",
  "startTime": "09:00:00",
  "endTime": "10:30:00",
  "calendarEventId": "abc123",
  "calendarEventTitle": "Physics report"
}
```

### Get / Update / Delete by id

| Method | Path | Result |
|--------|------|--------|
| `GET` | `/api/activities/{id}` | **200** `ActivityResponse` |
| `PUT` | `/api/activities/{id}` | **200** updated `ActivityResponse` (same body as create) |
| `DELETE` | `/api/activities/{id}` | **204 No Content** |

All are scoped to the authenticated user; another user's id yields **404**.

### Statistics

`GET /api/activities/stats?from=2026-05-01&to=2026-05-08`

**200 OK**

```json
{
  "totalSeconds": 18000,
  "totalHours": 5.0,
  "sessionCount": 6,
  "dailyBreakdown": [
    { "date": "2026-05-01", "seconds": 7200 },
    { "date": "2026-05-02", "seconds": 10800 }
  ]
}
```

### Assignable calendar events

`GET /api/activities/stats/assignable-events?date=2026-05-07`

Returns Google Calendar events in a ±1-day window around `date` (for the "assign session to
event" picker). Requires a connected Google account.

**200 OK** → array of `CalendarEventResponse`.

### Planned-vs-actual comparison

`GET /api/activities/stats/event-comparison?from=2026-05-04&to=2026-05-10`

Compares planned time (calendar events) against actual time (linked activity sessions) per title.

**200 OK**

```json
[
  {
    "eventTitle": "Physics report",
    "plannedSeconds": 7200,
    "actualSeconds": 5400,
    "deficitSeconds": -1800
  }
]
```

`deficitSeconds = actualSeconds − plannedSeconds` (negative = under-spent).

---

## Google Calendar OAuth — `/auth/google/calendar`

### Get consent URL

`GET /auth/google/calendar/connect`

Optional query param `appRedirectUri` for mobile (must be a **custom URI scheme** such as
`com.yourapp://oauth`; `http`/`https` are rejected to prevent open redirects).

**200 OK** → `{ "authUrl": "https://accounts.google.com/o/oauth2/v2/auth?..." }`

The client opens `authUrl`; Google then redirects to the callback below.

### OAuth callback

`GET /auth/google/calendar/callback?code=...&state=...` · **public**

Handled by the backend (not called directly by your app):

- Exchanges the `code` for access + refresh tokens and persists them for the user.
- **Web flow:** **200 OK** → `{ "message": "Google Calendar connected successfully" }`.
- **Mobile flow:** **302** redirect to `<appRedirectUri>?connected=true`.
- `error` present, or invalid `state` → **400**.

---

## Scheduling Engine — `/api/engine`

### Generate schedule

`POST /api/engine/generate`

```json
{
  "prompt": "Finish the physics report, prep for the calculus exam, reply to emails",
  "timeRange": {
    "from": "2026-06-01T08:00:00+00:00",
    "to":   "2026-06-07T20:00:00+00:00"
  },
  "percentage": 0.6,
  "recurrent": false,
  "schedulerType": "proportional"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `prompt` | string | natural-language description of the work |
| `timeRange.from` / `.to` | OffsetDateTime | scheduling window |
| `percentage` | number `(0,1]` | fraction of free time to fill |
| `recurrent` | boolean | if true, events get a weekly `RRULE` |
| `schedulerType` | string | `proportional` (default) · `physics` · `llm` |

Pipeline: extract tasks (LLM) → read calendar constraints → run the chosen scheduler → create the
events in Google Calendar. **Requires a connected Google account**; `llm`/extraction require
`OPENAI_API_KEY`.

**201 Created** → array of created `CalendarEventResponse`:

```json
[
  {
    "id": "googleEventId123",
    "title": "Physics report",
    "description": "Priority: 8 | Scheduled by Proportional Scheduler",
    "colorId": "5",
    "startDateTime": "2026-06-01T09:00:00+00:00",
    "endDateTime": "2026-06-01T10:00:00+00:00",
    "timeZone": "UTC",
    "htmlLink": "https://www.google.com/calendar/event?eid=...",
    "recurrence": []
  }
]
```

Unknown `schedulerType` → **400** (`IllegalArgumentException`).

### Roll back last schedule

`POST /api/engine/rollback`

Deletes every Google Calendar event created by the most recent `generate` call. Idempotent — a
second call is a no-op.

**200 OK** (empty body). If some deletions fail, responds with **500** and lists the event IDs
that could not be removed.

> Rollback state is held in memory on the service instance — see
> [docs/architecture.md §6](architecture.md#6-known-limitations--future-work).

---

## Reference enums

| Enum | Values |
|------|--------|
| `ActivityType` | `FOCUS`, `MEETING`, `BREAK`, `ROUTINE` |
| `CalendarColor` → colorId | `LAVENDER`=1, `SAGE`=2, `GRAPE`=3, `FLAMINGO`=4, `BANANA`=5, `TANGERINE`=6, `PEACOCK`=7, `BLUEBERRY`=8, `BASIL`=9, `TOMATO`=10, `GRAPHITE`=11 |

For manual exploration, import the Postman collection in
[`postman/collections/AI-scheduler/`](../postman/collections/AI-scheduler/).
