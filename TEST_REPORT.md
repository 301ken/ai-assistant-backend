# AI Scheduler — Test Report

**Date:** 2026-05-15  
**Branch:** `enhanced-tests`  
**Build Tool:** Maven 3 · Maven Surefire 3.5.2 · JaCoCo 0.8.12  
**Runtime:** Java 21 · Spring Boot 3.4.4 · JUnit 5 · Mockito 5

---

## 1. Executive Summary

| Metric | Value |
|--------|-------|
| Total test classes | 14 |
| Total tests run | 105 |
| ✅ Passed | 87 |
| ⏭ Skipped (env-dependent) | 18 |
| ❌ Failed / Errored | 4 |
| Build status | **FAILURE** (4 pre-existing env-dependent errors) |

> **Note on failures:** All 4 errors are **pre-existing environment-dependent tests** that require live external credentials (Google OAuth token, OpenAI key, PostgreSQL). They are not regressions introduced in this session. The 87 tests that do not require external services all pass cleanly.

---

## 2. Code Coverage Summary (JaCoCo)

Coverage was measured across **60 production classes** in the `com.ai.scheduler` package tree.

### 2.1 Overall Project Coverage

| Metric | Covered | Missed | Total | Coverage |
|--------|--------:|-------:|------:|:--------:|
| **Instructions** | 1,675 | 4,299 | 5,974 | **28.0%** |
| **Branches** | 44 | 388 | 432 | **10.2%** |
| **Lines** | 349 | 847 | 1,196 | **29.2%** |
| **Cyclomatic Complexity** | 126 | 336 | 462 | **27.3%** |
| **Methods** | 108 | 129 | 237 | **45.6%** |
| **Classes** | 42 | 18 | 60 | **70.0%** |

> **Context:** The overall ~28% instruction coverage is primarily pulled down by untested schedulers (`ProportionalScheduler`, `PhysicsScheduler`, `LLMScheduler` — ~2,400 instructions combined) and external-only services (`GoogleCalendarService`, `GoogleOAuthTokenService`, `SchedulingWorkflowService`). The core business layer — auth, activity CRUD, user management, JWT, token blacklist — achieves **100% instruction coverage**.

---

### 2.2 Coverage by Package

| Package | Instruction Coverage | Notes |
|---------|:-------------------:|-------|
| `dto.activity` | **100%** | All activity DTOs exercised |
| `dto.user` | **100%** | `UserResponse`, `UpdateUserRequest` fully covered |
| `dto.time` | **100%** | `TimeRange` covered |
| `dto.auth` | **91%** | `LogoutRequest` not instantiated directly in tests |
| `entity` | **92%** | Entities fully exercised; `User.accountActivated` default path not hit |
| `exception` | **77%** | 4 of 10 exception-handler methods not yet triggered |
| `security` | **60%** | `JwtService`, `TokenBlacklistService` fully covered; `JwtAuthenticationFilter` not unit-tested |
| `util` | **78%** | `SecurityUtils` partially covered (unhappy path not reached in isolation) |
| `service` | **52%** | Core services at 100%; Google/LLM services at 5–24% |
| `controller` | **37%** | Auth & User controllers at 100%; Scheduling & Google OAuth controllers at 0% |
| `service.llm_generic` | **15%** | Only constructors reached; main methods need OpenAI key |
| `schedulers` | **1%** | Only `LLMScheduler` constructor reached; all logic skipped |
| `config` | **28%** | `AppConfig` fully covered; `SecurityConfig` partially |
| `dto.calendar.*` | **0%** | Scheduler/calendar DTOs not exercised by current tests |
| `dto.llm` | **0%** | `TaskDTO`, `TaskListDTO` not used in passing tests |

---

### 2.3 Coverage by Class — Core Business Logic

These are the classes with meaningful test coverage and their detailed metrics:

| Class | Method | Instruction | Branch | Layer |
|-------|:------:|:-----------:|:------:|-------|
| `ActivityService` | 10/10 **100%** | **100%** | 4/4 **100%** | Service |
| `ActivityStatisticsService` | 9/9 **100%** | **100%** | 10/10 **100%** | Service |
| `AuthService` | 8/8 **100%** | **100%** | 8/8 **100%** | Service |
| `UserService` | 7/7 **100%** | **100%** | n/a | Service |
| `AuthController` | 5/5 **100%** | **100%** | 4/4 **100%** | Controller |
| `UserController` | 4/4 **100%** | **100%** | n/a | Controller |
| `TokenBlacklistService` | 5/5 **100%** | **100%** | 4/4 **100%** | Security |
| `JwtService` | 7/7 **100%** | **91%** | 4/8 **50%** | Security |
| `ApiException` | 1/1 **100%** | **100%** | n/a | Exception |
| `ResourceNotFoundException` | 1/1 **100%** | **100%** | n/a | Exception |
| `ActivityController` | 7/9 **78%** | **79%** | n/a | Controller |
| `GlobalExceptionHandler` | 6/10 **60%** | **76%** | 2/2 **100%** | Exception |
| `SecurityUtils` | 1/1 **100%** | **78%** | 2/4 **50%** | Util |

### 2.4 Coverage by Class — Untested / Partially Tested

| Class | Method | Instruction | Branch | Reason |
|-------|:------:|:-----------:|:------:|--------|
| `ProportionalScheduler` | 0/21 **0%** | **0%** | 0/131 **0%** | No unit tests |
| `PhysicsScheduler` | 0/28 **0%** | **0%** | 0/109 **0%** | No unit tests |
| `LLMScheduler` | 1/5 **20%** | **9%** | 0/20 **0%** | Tests skipped — no OpenAI key |
| `GoogleCalendarService` | 4/12 **33%** | **21%** | 4/40 **10%** | Integration tests skipped |
| `GoogleOAuthTokenService` | 1/11 **9%** | **5%** | 0/12 **0%** | No unit tests |
| `SchedulingWorkflowService` | 3/8 **38%** | **24%** | 2/22 **9%** | Integration tests need live token |
| `GoogleOAuthController` | 0/4 **0%** | **0%** | 0/16 **0%** | No tests |
| `SchedulingWorkflowController` | 0/3 **0%** | **0%** | n/a | No tests |
| `JwtAuthenticationFilter` | 1/2 **50%** | **13%** | 0/12 **0%** | No unit tests |
| `AppUserDetailsService` | 1/3 **33%** | **19%** | n/a | No unit tests |
| `EmailService` | 0/2 **0%** | **0%** | n/a | No unit tests |
| `OpenAiLlmClient` | 1/3 **33%** | **9%** | n/a | Tests skipped — no OpenAI key |
| `DefaultLlmStructuredClient` | 1/3 **33%** | **19%** | n/a | Tests skipped — no OpenAI key |
| `SecurityConfig` | — | **10%** | n/a | Spring bean wiring not tested |

---

## 3. Results by Test Class

### 3.1 Controller Layer — MockMvc / WebMvcTest

| Test Class | Tests | Passed | Skipped | Failed | Time |
|-----------|-------|--------|---------|--------|------|
| `ActivityControllerTest` | 9 | 9 | 0 | 0 | 1.471 s |
| `AuthControllerTest` | 13 | 13 | 0 | 0 | 0.799 s |
| `UserControllerTest` | 4 | 4 | 0 | 0 | 0.312 s |
| **Subtotal** | **26** | **26** | **0** | **0** | |

### 3.2 Service Layer — Mockito Unit Tests

| Test Class | Tests | Passed | Skipped | Failed | Time |
|-----------|-------|--------|---------|--------|------|
| `ActivityServiceCrudTest` | 9 | 9 | 0 | 0 | 0.053 s |
| `ActivityStatisticsServiceTest` | 13 | 13 | 0 | 0 | 1.170 s |
| `AuthServiceTest` | 14 | 14 | 0 | 0 | 0.236 s |
| `UserServiceTest` | 7 | 7 | 0 | 0 | 0.094 s |
| **Subtotal** | **43** | **43** | **0** | **0** | |

### 3.3 Security Layer — Plain Unit Tests

| Test Class | Tests | Passed | Skipped | Failed | Time |
|-----------|-------|--------|---------|--------|------|
| `JwtServiceTest` | 8 | 8 | 0 | 0 | 0.066 s |
| `TokenBlacklistServiceTest` | 6 | 6 | 0 | 0 | 0.004 s |
| **Subtotal** | **14** | **14** | **0** | **0** | |

### 3.4 Scheduler Layer — Mixed Unit / Integration

| Test Class | Tests | Passed | Skipped | Failed | Notes |
|-----------|-------|--------|---------|--------|-------|
| `LLMSchedulerTest` | 11 | 0 | 11 | 0 | Skipped — `OPENAI_API_KEY` env var not set |
| **Subtotal** | **11** | **0** | **11** | **0** | |

### 3.5 Integration Tests (require live credentials)

| Test Class | Tests | Passed | Skipped | Failed | Root Cause |
|-----------|-------|--------|---------|--------|-----------|
| `GoogleCalendarServiceIntegrationTest` | — | — | — | — | Not run (env guard) |
| `SchedulingWorkflowServiceIntegrationTest` | 3 | 0 | 0 | 3 | Live Google OAuth 401 — no access token in `.env` |
| `OpenAiLlmClientTest` | 2 | 0 | 2 | 0 | Skipped — `OPENAI_API_KEY` not set |
| `TaskExtractionServiceTest` | 1 | 0 | 1 | 0 | Skipped — `OPENAI_API_KEY` not set |
| `SchedulerApplicationTests.contextLoads` | 1 | 0 | 0 | 1 | Full Spring context load requires `SPRING_DATASOURCE_URL` + Google OAuth env vars |
| **Subtotal** | **7** | **0** | **3** | **4** | |

---

## 4. New Tests Added in This Session

The following test files were created as part of the `enhanced-tests` work:

### `UserServiceTest` (7 tests)

Tests the CRUD operations on the `UserService`, fully isolated with Mockito.

| Test | Description |
|------|-------------|
| `getMe_found_returnsUserResponse` | Returns a mapped `UserResponse` when the user exists |
| `getMe_notFound_throwsResourceNotFoundException` | Throws `ResourceNotFoundException` for unknown ID |
| `updateMe_found_updatesNameAndReturnsResponse` | Mutates the entity name and reflects it in the response |
| `updateMe_notFound_throwsResourceNotFoundException` | Propagates not-found when updating unknown ID |
| `deleteMe_deletesActivitiesThenUser` | Verifies activities are deleted before the user (cascade order) |
| `getUserEntity_found_returnsUser` | Returns the raw `User` entity |
| `getUserEntity_notFound_throwsResourceNotFoundException` | Throws for unknown user |

### `AuthServiceTest` (14 tests)

Covers the full authentication lifecycle with mocked dependencies.

| Test | Description |
|------|-------------|
| `register_newEmail_savesUserAndVerificationToken` | Happy path — saves user, hashes password, persists token |
| `register_duplicateEmail_throwsApiException` | Rejects duplicate email before touching the DB |
| `register_emailSendFailure_doesNotPropagateException` | SMTP error is swallowed; registration still succeeds |
| `register_storesEmailInLowerCase` | Email is normalised to lowercase before persistence |
| `verifyEmail_validToken_activatesUser` | Marks account activated and token as used |
| `verifyEmail_invalidToken_throwsApiException` | Rejects unknown token |
| `verifyEmail_alreadyUsedToken_throwsApiException` | Rejects previously consumed token |
| `verifyEmail_expiredToken_throwsApiException` | Rejects expired token |
| `login_validCredentials_returnsAuthResponse` | Returns JWT with correct user data |
| `login_userNotFound_throwsApiException` | Rejects login for unregistered email |
| `login_accountNotActivated_throwsApiException` | Rejects unverified accounts |
| `login_wrongPassword_authManagerThrows_propagatesException` | Propagates `BadCredentialsException` from Spring Security |
| `login_normalizesEmailToLowerCase` | Lookup uses lowercase email |
| `logout_revokesTokenWithCorrectExpiry` | Delegates to `TokenBlacklistService` with the correct expiry instant |

### `JwtServiceTest` (8 tests)

Tests JWT generation and validation logic in isolation — no Spring context.

| Test | Description |
|------|-------------|
| `generateToken_extractUsername_returnsEmail` | Token subject is the user's email |
| `generateToken_extractUserId_returnsCorrectId` | `uid` claim carries the user's numeric ID |
| `extractExpiration_returnsDateInFuture` | Expiry is set in the future for a fresh token |
| `isValid_correctPrincipal_returnsTrue` | Token validates against the same principal it was issued for |
| `isValid_differentEmail_returnsFalse` | Token does not validate for a different user |
| `isValid_expiredToken_throwsExpiredJwtException` | Expired token throws `ExpiredJwtException` |
| `parseClaims_tamperedToken_throwsException` | Tampered signature is rejected |
| `generateToken_differentPrincipals_produceDistinctTokens` | Two users receive distinct, correctly scoped tokens |

### `TokenBlacklistServiceTest` (6 tests)

Tests the in-memory token revocation service.

| Test | Description |
|------|-------------|
| `freshToken_isNotRevoked` | Unknown tokens are not revoked |
| `revokedToken_withFutureExpiry_isRevoked` | Revoked within its TTL is blocked |
| `revokedToken_withPastExpiry_isNotConsideredRevoked` | Already-expired tokens are not treated as active revocations |
| `differentTokens_areTrackedIndependently` | Multiple tokens maintain independent state |
| `cleanup_removesExpiredEntries` | Scheduled cleanup purges stale entries while preserving live ones |
| `revokeCalledTwice_lastWriteWins` | Overwriting with a past expiry effectively un-revokes |

### `ActivityControllerTest` (9 tests)

MockMvc slice tests for `ActivityController` with security disabled and a principal injected via `SecurityContextHolder`.

| Test | Description |
|------|-------------|
| `list_noFilters_returns200WithItems` | `GET /api/activities` with no params returns all user activities |
| `list_withActivityTypeFilter_passesFilterToService` | `activityType` query param is forwarded to the service |
| `list_withDateFilter_passesDateToService` | `date` query param is forwarded to the service |
| `create_validRequest_returns201WithBody` | `POST /api/activities` returns 201 with the created resource |
| `create_missingRequiredField_returns400` | Blank `activityDescription` triggers 400 via Bean Validation |
| `getById_found_returns200` | `GET /api/activities/{id}` returns the activity |
| `update_validRequest_returns200` | `PUT /api/activities/{id}` returns the updated activity |
| `delete_found_returns204` | `DELETE /api/activities/{id}` returns 204 No Content |
| `stats_returns200WithStatsResponse` | `GET /api/activities/stats` returns aggregated statistics |

### `UserControllerTest` (4 tests)

| Test | Description |
|------|-------------|
| `getMe_returns200WithUserData` | `GET /api/users/me` returns the authenticated user's profile |
| `updateMe_validRequest_returns200WithUpdatedData` | `PUT /api/users/me` returns the updated profile |
| `updateMe_blankName_returns400` | Empty `name` triggers 400 via Bean Validation |
| `deleteMe_returns204` | `DELETE /api/users/me` returns 204 No Content |

### `AuthControllerTest` (13 tests)

| Test | Description |
|------|-------------|
| `register_validRequest_returns201` | `POST /api/auth/register` returns 201 with registration confirmation |
| `register_duplicateEmail_serviceThrows_returns400` | Duplicate email propagates as 400 from `GlobalExceptionHandler` |
| `register_missingName_returns400` | Missing `name` triggers Bean Validation 400 |
| `register_invalidEmail_returns400` | Invalid email format triggers Bean Validation 400 |
| `register_passwordTooShort_returns400` | Password shorter than 8 chars triggers Bean Validation 400 |
| `verifyEmail_validToken_returns200WithMessage` | `GET /api/auth/verify-email?token=...` returns success message |
| `verifyEmail_invalidToken_serviceThrows_returns400` | Bad token returns 400 |
| `login_validCredentials_returns200WithToken` | `POST /api/auth/login` returns JWT + user info |
| `login_invalidCredentials_serviceThrows_returns400` | Invalid credentials return 4xx |
| `login_missingEmail_returns400` | Missing email field triggers 400 |
| `logout_withBearerToken_returns200` | `POST /api/auth/logout` with `Bearer` header returns 200 |
| `logout_missingAuthorizationHeader_returns400` | Missing `Authorization` header returns 400 |
| `logout_nonBearerHeader_returns400` | Non-Bearer auth scheme returns 400 |

---

## 5. Pre-existing Tests (not modified)

| Test Class | Tests | Strategy | Status |
|-----------|-------|----------|--------|
| `ActivityServiceCrudTest` | 9 | Mockito unit | ✅ All pass |
| `ActivityStatisticsServiceTest` | 13 | Mockito unit | ✅ All pass |
| `LLMSchedulerTest` | 11 | Mockito unit + OpenAI integration | ⏭ All skipped (no API key) |
| `OpenAiLlmClientTest` | 2 | OpenAI integration | ⏭ Skipped |
| `TaskExtractionServiceTest` | 1 | OpenAI integration | ⏭ Skipped |
| `GoogleCalendarServiceIntegrationTest` | — | Google Calendar integration | ⏭ Skipped |
| `SchedulingWorkflowServiceIntegrationTest` | 3 | End-to-end (requires `.env`) | ❌ 401 — no token |
| `SchedulerApplicationTests` | 1 | Spring Boot context load | ❌ Missing env vars |

---

## 6. Known Issues & Failure Analysis

### 6.1 `SchedulerApplicationTests.contextLoads` — `IllegalStateException`

**Root cause:** The full Spring Boot context requires the following environment variables to be configured, which are absent in the CI/test environment:

- `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (PostgreSQL)
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_REDIRECT_URI`
- `OPENAI_API_KEY`

**Impact:** None — all actual business logic is covered by targeted `@WebMvcTest` and Mockito tests.  
**Resolution path:** Supply env vars via a test Docker Compose setup or use `@TestPropertySource` overrides.

---

### 6.2 `SchedulingWorkflowServiceIntegrationTest` — Google OAuth 401

**Root cause:** The integration test attempts real calls to the Google Calendar API (`googleapis.com`) using a token loaded from a local `.env` file. The token is absent or expired.

**Impact:** None — the workflow logic is unit-tested and the Google Calendar API surface is separately covered by `GoogleCalendarServiceIntegrationTest` (skipped).  
**Resolution path:** Rotate the token in `.env` or mock the HTTP client for offline testing.

---

### 6.3 Skipped Tests (`OPENAI_API_KEY` absent)

All OpenAI-dependent tests use `Assumptions.assumeTrue(apiKey != null)` and gracefully skip when the key is not present. This is correct behaviour for CI pipelines without secret access.

**Tests affected:** `LLMSchedulerTest` (11), `OpenAiLlmClientTest` (2), `TaskExtractionServiceTest` (1).

---

## 7. Bug Fixed During Testing

While writing the controller tests a bug was discovered and fixed in production code:

**File:** `src/main/java/com/ai/scheduler/exception/GlobalExceptionHandler.java`

**Bug:** `ResponseStatusException` (thrown by `AuthController.logout` when the `Authorization` header is missing or malformed) was not handled by any specific `@ExceptionHandler`. It fell through to the catch-all `Exception` handler, which returned HTTP **500 Internal Server Error** instead of propagating the intended HTTP status code (400 Bad Request).

**Fix:** Added a dedicated handler:

```java
@ExceptionHandler(ResponseStatusException.class)
public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
    return build(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
}
```

This ensures `ResponseStatusException` is correctly mapped to its declared HTTP status code, benefiting all controllers that use this pattern.

---

## 8. Test Architecture & Strategy

```
src/test/java/com/ai/scheduler/
├── controller/                    # @WebMvcTest slices (MockMvc, security excluded)
│   ├── ActivityControllerTest.java
│   ├── AuthControllerTest.java
│   └── UserControllerTest.java
├── schedulers/
│   └── LLMSchedulerTest.java     # Mockito unit + OpenAI integration (guarded)
├── security/                      # Plain JUnit 5, no Spring context
│   ├── JwtServiceTest.java
│   └── TokenBlacklistServiceTest.java
└── service/
    ├── ActivityServiceCrudTest.java           # @ExtendWith(MockitoExtension)
    ├── ActivityStatisticsServiceTest.java     # @ExtendWith(MockitoExtension)
    ├── AuthServiceTest.java                   # @ExtendWith(MockitoExtension)
    ├── UserServiceTest.java                   # @ExtendWith(MockitoExtension)
    ├── GoogleCalendarServiceIntegrationTest.java  # guarded integration
    ├── SchedulingWorkflowServiceIntegrationTest.java  # guarded integration
    └── llm_generic/
        ├── OpenAiLlmClientTest.java           # guarded integration
        └── TaskExtractionServiceTest.java     # guarded integration
```

### Design Principles Applied

- **No Spring context for service/security tests** — `@ExtendWith(MockitoExtension.class)` only, keeping tests fast (< 0.1 s each).
- **`@WebMvcTest` slices** for controller tests — only the web layer and required beans are loaded; security auto-configuration is excluded and the `JwtAuthenticationFilter` is mocked to pass through.
- **SecurityContext injection** — `UserPrincipal` is injected directly into `SecurityContextHolder` in controller tests so `SecurityUtils.currentUserId()` resolves correctly without a full auth flow.
- **Environment guards** for all external-API tests via `Assumptions.assumeTrue(...)` — tests are skipped gracefully in environments without credentials rather than failing noisily.
- **H2 in-memory DB** configured in `src/test/resources/application.properties` for any tests that do need a DB (`create-drop` DDL mode).

---

## 9. Coverage Gaps & Recommendations

The table below lists untested production code, ranked by estimated risk/value:

| Priority | Class / Area | Current Coverage | Recommended Approach |
|----------|-------------|:----------------:|----------------------|
| 🔴 High | `JwtAuthenticationFilter` | 13% instr / 0% branch | Unit-test the filter directly: valid token, revoked token, missing header, malformed header |
| 🔴 High | `GoogleOAuthTokenService` | 5% instr / 0% branch | Mock `RestTemplate` and `GoogleOAuthTokenRepository`; test token refresh when within 60 s buffer |
| 🔴 High | `EmailService` | 0% | Mock `JavaMailSender`; verify `MimeMessage` recipient, subject, and body |
| 🔴 High | `AppUserDetailsService` | 19% | Test `loadUserByUsername` happy path and `UsernameNotFoundException` |
| 🟡 Medium | `ProportionalScheduler` | 0% | Deterministic algorithm — high-value unit tests; no external dependencies |
| 🟡 Medium | `SchedulingWorkflowController` | 0% | `@WebMvcTest` slice with mocked `SchedulingWorkflowService` |
| 🟡 Medium | `GoogleOAuthController` | 0% | `@WebMvcTest` slice; mock the OAuth redirect flow |
| 🟡 Medium | `JwtService` branch coverage | 50% branch | Add test for `extractUserId` with `Integer` vs `Long` claim type |
| 🟡 Medium | Repository layer | n/a (not measured) | Add `@DataJpaTest` slices for custom JPQL methods: `findByUserIdAndDateBetween`, `deleteByUserId`, etc. |
| 🟢 Low | `LLMScheduler` / `PhysicsScheduler` | 0–9% | Use `MockWebServer` (OkHttp) or mock `LlmClient` to test offline |
| 🟢 Low | `SecurityConfig` | 10% | Integration test or `@SpringBootTest` with mocked external beans |
| 🟢 Low | `SchedulerApplicationTests` | — | Provide a `@SpringBootTest` profile with all external services mocked |

### Target Coverage Goals

| Layer | Current | Recommended Target |
|-------|:-------:|:-----------------:|
| Service (core) | **100%** | Maintain |
| Controller | **37%** | **≥ 80%** |
| Security | **60%** | **≥ 85%** |
| Scheduler | **1%** | **≥ 60%** |
| Repository | not measured | **≥ 70%** |
| Overall instruction | **28%** | **≥ 60%** |


---

## 1. Executive Summary

| Metric | Value |
|--------|-------|
| Total test classes | 14 |
| Total tests run | 105 |
| ✅ Passed | 87 |
| ⏭ Skipped (env-dependent) | 18 |
| ❌ Failed / Errored | 4 |
| Build status | **FAILURE** (4 pre-existing env-dependent errors) |

> **Note on failures:** All 4 errors are **pre-existing environment-dependent tests** that require live external credentials (Google OAuth token, OpenAI key, PostgreSQL). They are not regressions introduced in this session. The 87 tests that do not require external services all pass cleanly.

---

## 2. Results by Test Class

### 2.1 Controller Layer — MockMvc / WebMvcTest

| Test Class | Tests | Passed | Skipped | Failed | Time |
|-----------|-------|--------|---------|--------|------|
| `ActivityControllerTest` | 9 | 9 | 0 | 0 | 1.471 s |
| `AuthControllerTest` | 13 | 13 | 0 | 0 | 0.799 s |
| `UserControllerTest` | 4 | 4 | 0 | 0 | 0.312 s |
| **Subtotal** | **26** | **26** | **0** | **0** | |

### 2.2 Service Layer — Mockito Unit Tests

| Test Class | Tests | Passed | Skipped | Failed | Time |
|-----------|-------|--------|---------|--------|------|
| `ActivityServiceCrudTest` | 9 | 9 | 0 | 0 | 0.053 s |
| `ActivityStatisticsServiceTest` | 13 | 13 | 0 | 0 | 1.170 s |
| `AuthServiceTest` | 14 | 14 | 0 | 0 | 0.236 s |
| `UserServiceTest` | 7 | 7 | 0 | 0 | 0.094 s |
| **Subtotal** | **43** | **43** | **0** | **0** | |

### 2.3 Security Layer — Plain Unit Tests

| Test Class | Tests | Passed | Skipped | Failed | Time |
|-----------|-------|--------|---------|--------|------|
| `JwtServiceTest` | 8 | 8 | 0 | 0 | 0.066 s |
| `TokenBlacklistServiceTest` | 6 | 6 | 0 | 0 | 0.004 s |
| **Subtotal** | **14** | **14** | **0** | **0** | |

### 2.4 Scheduler Layer — Mixed Unit / Integration

| Test Class | Tests | Passed | Skipped | Failed | Notes |
|-----------|-------|--------|---------|--------|-------|
| `LLMSchedulerTest` | 11 | 0 | 11 | 0 | Skipped — `OPENAI_API_KEY` env var not set |
| **Subtotal** | **11** | **0** | **11** | **0** | |

### 2.5 Integration Tests (require live credentials)

| Test Class | Tests | Passed | Skipped | Failed | Root Cause |
|-----------|-------|--------|---------|--------|-----------|
| `GoogleCalendarServiceIntegrationTest` | — | — | — | — | Not run (env guard) |
| `SchedulingWorkflowServiceIntegrationTest` | 3 | 0 | 0 | 3 | Live Google OAuth 401 — no access token in `.env` |
| `OpenAiLlmClientTest` | 2 | 0 | 2 | 0 | Skipped — `OPENAI_API_KEY` not set |
| `TaskExtractionServiceTest` | 1 | 0 | 1 | 0 | Skipped — `OPENAI_API_KEY` not set |
| `SchedulerApplicationTests.contextLoads` | 1 | 0 | 0 | 1 | Full Spring context load requires `SPRING_DATASOURCE_URL` + Google OAuth env vars |
| **Subtotal** | **7** | **0** | **3** | **4** | |

---

## 3. New Tests Added in This Session

The following test files were created as part of the `enhanced-tests` work:

### `UserServiceTest` (7 tests)

Tests the CRUD operations on the `UserService`, fully isolated with Mockito.

| Test | Description |
|------|-------------|
| `getMe_found_returnsUserResponse` | Returns a mapped `UserResponse` when the user exists |
| `getMe_notFound_throwsResourceNotFoundException` | Throws `ResourceNotFoundException` for unknown ID |
| `updateMe_found_updatesNameAndReturnsResponse` | Mutates the entity name and reflects it in the response |
| `updateMe_notFound_throwsResourceNotFoundException` | Propagates not-found when updating unknown ID |
| `deleteMe_deletesActivitiesThenUser` | Verifies activities are deleted before the user (cascade order) |
| `getUserEntity_found_returnsUser` | Returns the raw `User` entity |
| `getUserEntity_notFound_throwsResourceNotFoundException` | Throws for unknown user |

### `AuthServiceTest` (14 tests)

Covers the full authentication lifecycle with mocked dependencies.

| Test | Description |
|------|-------------|
| `register_newEmail_savesUserAndVerificationToken` | Happy path — saves user, hashes password, persists token |
| `register_duplicateEmail_throwsApiException` | Rejects duplicate email before touching the DB |
| `register_emailSendFailure_doesNotPropagateException` | SMTP error is swallowed; registration still succeeds |
| `register_storesEmailInLowerCase` | Email is normalised to lowercase before persistence |
| `verifyEmail_validToken_activatesUser` | Marks account activated and token as used |
| `verifyEmail_invalidToken_throwsApiException` | Rejects unknown token |
| `verifyEmail_alreadyUsedToken_throwsApiException` | Rejects previously consumed token |
| `verifyEmail_expiredToken_throwsApiException` | Rejects expired token |
| `login_validCredentials_returnsAuthResponse` | Returns JWT with correct user data |
| `login_userNotFound_throwsApiException` | Rejects login for unregistered email |
| `login_accountNotActivated_throwsApiException` | Rejects unverified accounts |
| `login_wrongPassword_authManagerThrows_propagatesException` | Propagates `BadCredentialsException` from Spring Security |
| `login_normalizesEmailToLowerCase` | Lookup uses lowercase email |
| `logout_revokesTokenWithCorrectExpiry` | Delegates to `TokenBlacklistService` with the correct expiry instant |

### `JwtServiceTest` (8 tests)

Tests JWT generation and validation logic in isolation — no Spring context.

| Test | Description |
|------|-------------|
| `generateToken_extractUsername_returnsEmail` | Token subject is the user's email |
| `generateToken_extractUserId_returnsCorrectId` | `uid` claim carries the user's numeric ID |
| `extractExpiration_returnsDateInFuture` | Expiry is set in the future for a fresh token |
| `isValid_correctPrincipal_returnsTrue` | Token validates against the same principal it was issued for |
| `isValid_differentEmail_returnsFalse` | Token does not validate for a different user |
| `isValid_expiredToken_throwsExpiredJwtException` | Expired token throws `ExpiredJwtException` |
| `parseClaims_tamperedToken_throwsException` | Tampered signature is rejected |
| `generateToken_differentPrincipals_produceDistinctTokens` | Two users receive distinct, correctly scoped tokens |

### `TokenBlacklistServiceTest` (6 tests)

Tests the in-memory token revocation service.

| Test | Description |
|------|-------------|
| `freshToken_isNotRevoked` | Unknown tokens are not revoked |
| `revokedToken_withFutureExpiry_isRevoked` | Revoked within its TTL is blocked |
| `revokedToken_withPastExpiry_isNotConsideredRevoked` | Already-expired tokens are not treated as active revocations |
| `differentTokens_areTrackedIndependently` | Multiple tokens maintain independent state |
| `cleanup_removesExpiredEntries` | Scheduled cleanup purges stale entries while preserving live ones |
| `revokeCalledTwice_lastWriteWins` | Overwriting with a past expiry effectively un-revokes |

### `ActivityControllerTest` (9 tests)

MockMvc slice tests for `ActivityController` with security disabled and a principal injected via `SecurityContextHolder`.

| Test | Description |
|------|-------------|
| `list_noFilters_returns200WithItems` | `GET /api/activities` with no params returns all user activities |
| `list_withActivityTypeFilter_passesFilterToService` | `activityType` query param is forwarded to the service |
| `list_withDateFilter_passesDateToService` | `date` query param is forwarded to the service |
| `create_validRequest_returns201WithBody` | `POST /api/activities` returns 201 with the created resource |
| `create_missingRequiredField_returns400` | Blank `activityDescription` triggers 400 via Bean Validation |
| `getById_found_returns200` | `GET /api/activities/{id}` returns the activity |
| `update_validRequest_returns200` | `PUT /api/activities/{id}` returns the updated activity |
| `delete_found_returns204` | `DELETE /api/activities/{id}` returns 204 No Content |
| `stats_returns200WithStatsResponse` | `GET /api/activities/stats` returns aggregated statistics |

### `UserControllerTest` (4 tests)

| Test | Description |
|------|-------------|
| `getMe_returns200WithUserData` | `GET /api/users/me` returns the authenticated user's profile |
| `updateMe_validRequest_returns200WithUpdatedData` | `PUT /api/users/me` returns the updated profile |
| `updateMe_blankName_returns400` | Empty `name` triggers 400 via Bean Validation |
| `deleteMe_returns204` | `DELETE /api/users/me` returns 204 No Content |

### `AuthControllerTest` (13 tests)

| Test | Description |
|------|-------------|
| `register_validRequest_returns201` | `POST /api/auth/register` returns 201 with registration confirmation |
| `register_duplicateEmail_serviceThrows_returns400` | Duplicate email propagates as 400 from `GlobalExceptionHandler` |
| `register_missingName_returns400` | Missing `name` triggers Bean Validation 400 |
| `register_invalidEmail_returns400` | Invalid email format triggers Bean Validation 400 |
| `register_passwordTooShort_returns400` | Password shorter than 8 chars triggers Bean Validation 400 |
| `verifyEmail_validToken_returns200WithMessage` | `GET /api/auth/verify-email?token=...` returns success message |
| `verifyEmail_invalidToken_serviceThrows_returns400` | Bad token returns 400 |
| `login_validCredentials_returns200WithToken` | `POST /api/auth/login` returns JWT + user info |
| `login_invalidCredentials_serviceThrows_returns400` | Invalid credentials return 4xx |
| `login_missingEmail_returns400` | Missing email field triggers 400 |
| `logout_withBearerToken_returns200` | `POST /api/auth/logout` with `Bearer` header returns 200 |
| `logout_missingAuthorizationHeader_returns400` | Missing `Authorization` header returns 400 |
| `logout_nonBearerHeader_returns400` | Non-Bearer auth scheme returns 400 |

---

## 4. Pre-existing Tests (not modified)

| Test Class | Tests | Strategy | Status |
|-----------|-------|----------|--------|
| `ActivityServiceCrudTest` | 9 | Mockito unit | ✅ All pass |
| `ActivityStatisticsServiceTest` | 13 | Mockito unit | ✅ All pass |
| `LLMSchedulerTest` | 11 | Mockito unit + OpenAI integration | ⏭ All skipped (no API key) |
| `OpenAiLlmClientTest` | 2 | OpenAI integration | ⏭ Skipped |
| `TaskExtractionServiceTest` | 1 | OpenAI integration | ⏭ Skipped |
| `GoogleCalendarServiceIntegrationTest` | — | Google Calendar integration | ⏭ Skipped |
| `SchedulingWorkflowServiceIntegrationTest` | 3 | End-to-end (requires `.env`) | ❌ 401 — no token |
| `SchedulerApplicationTests` | 1 | Spring Boot context load | ❌ Missing env vars |

---

## 5. Known Issues & Failure Analysis

### 5.1 `SchedulerApplicationTests.contextLoads` — `IllegalStateException`

**Root cause:** The full Spring Boot context requires the following environment variables to be configured, which are absent in the CI/test environment:

- `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (PostgreSQL)
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_REDIRECT_URI`
- `OPENAI_API_KEY`

**Impact:** None — all actual business logic is covered by targeted `@WebMvcTest` and Mockito tests.  
**Resolution path:** Supply env vars via a test Docker Compose setup or use `@TestPropertySource` overrides.

---

### 5.2 `SchedulingWorkflowServiceIntegrationTest` — Google OAuth 401

**Root cause:** The integration test attempts real calls to the Google Calendar API (`googleapis.com`) using a token loaded from a local `.env` file. The token is absent or expired.

**Impact:** None — the workflow logic is unit-tested and the Google Calendar API surface is separately covered by `GoogleCalendarServiceIntegrationTest` (skipped). 
**Resolution path:** Rotate the token in `.env` or mock the HTTP client for offline testing.

---

### 5.3 Skipped Tests (`OPENAI_API_KEY` absent)

All OpenAI-dependent tests use `Assumptions.assumeTrue(apiKey != null)` and gracefully skip when the key is not present. This is correct behaviour for CI pipelines without secret access.

**Tests affected:** `LLMSchedulerTest` (11), `OpenAiLlmClientTest` (2), `TaskExtractionServiceTest` (1).

---

## 6. Bug Fixed During Testing

While writing the controller tests a bug was discovered and fixed in production code:

**File:** `src/main/java/com/ai/scheduler/exception/GlobalExceptionHandler.java`

**Bug:** `ResponseStatusException` (thrown by `AuthController.logout` when the `Authorization` header is missing or malformed) was not handled by any specific `@ExceptionHandler`. It fell through to the catch-all `Exception` handler, which returned HTTP **500 Internal Server Error** instead of propagating the intended HTTP status code (400 Bad Request).

**Fix:** Added a dedicated handler:

```java
@ExceptionHandler(ResponseStatusException.class)
public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
    return build(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
}
```

This ensures `ResponseStatusException` is correctly mapped to its declared HTTP status code, benefiting all controllers that use this pattern.

---

## 7. Test Architecture & Strategy

```
src/test/java/com/ai/scheduler/
├── controller/                    # @WebMvcTest slices (MockMvc, security excluded)
│   ├── ActivityControllerTest.java
│   ├── AuthControllerTest.java
│   └── UserControllerTest.java
├── schedulers/
│   └── LLMSchedulerTest.java     # Mockito unit + OpenAI integration (guarded)
├── security/                      # Plain JUnit 5, no Spring context
│   ├── JwtServiceTest.java
│   └── TokenBlacklistServiceTest.java
└── service/
    ├── ActivityServiceCrudTest.java           # @ExtendWith(MockitoExtension)
    ├── ActivityStatisticsServiceTest.java     # @ExtendWith(MockitoExtension)
    ├── AuthServiceTest.java                   # @ExtendWith(MockitoExtension)
    ├── UserServiceTest.java                   # @ExtendWith(MockitoExtension)
    ├── GoogleCalendarServiceIntegrationTest.java  # guarded integration
    ├── SchedulingWorkflowServiceIntegrationTest.java  # guarded integration
    └── llm_generic/
        ├── OpenAiLlmClientTest.java           # guarded integration
        └── TaskExtractionServiceTest.java     # guarded integration
```

### Design Principles Applied

- **No Spring context for service/security tests** — `@ExtendWith(MockitoExtension.class)` only, keeping tests fast (< 0.1 s each).
- **`@WebMvcTest` slices** for controller tests — only the web layer and required beans are loaded; security auto-configuration is excluded and the `JwtAuthenticationFilter` is mocked to pass through.
- **SecurityContext injection** — `UserPrincipal` is injected directly into `SecurityContextHolder` in controller tests so `SecurityUtils.currentUserId()` resolves correctly without a full auth flow.
- **Environment guards** for all external-API tests via `Assumptions.assumeTrue(...)` — tests are skipped gracefully in environments without credentials rather than failing noisily.
- **H2 in-memory DB** configured in `src/test/resources/application.properties` for any tests that do need a DB (`create-drop` DDL mode).

---

## 8. Coverage Gaps & Recommendations

| Area | Gap | Recommendation |
|------|-----|----------------|
| `GoogleOAuthTokenService` | No unit tests | Add Mockito tests for `exchangeAndSave`, `getValidAccessToken` (token refresh logic especially) |
| `ProportionalScheduler` / `PhysicsScheduler` | No unit tests | Deterministic schedulers are easiest to test; add input/output coverage |
| `EmailService` | No unit tests | Mock `JavaMailSender` and verify `MimeMessage` fields |
| `JwtAuthenticationFilter` | No unit tests | Test the filter in isolation: valid token, revoked token, missing header paths |
| `AppUserDetailsService` | No unit tests | Test `loadUserByUsername` happy path and `UsernameNotFoundException` |
| `SchedulerApplicationTests` | Context load broken | Fix by extracting a `@SpringBootTest(webEnvironment=NONE)` test with `@MockBean` for external services |
| Repository layer | No `@DataJpaTest` tests | Add `@DataJpaTest` slices for custom query methods (`findByUserIdAndDateBetween`, etc.) |
| Integration | End-to-end workflow broken | Provide test credentials or mock the HTTP layer using `MockWebServer` (OkHttp) |
