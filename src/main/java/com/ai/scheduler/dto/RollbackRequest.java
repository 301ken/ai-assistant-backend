package com.ai.scheduler.dto;

import java.util.List;

/**
 * Body for {@code POST /api/engine/rollback}.
 *
 * <p>The IDs of the events to delete are supplied by the caller — they are the
 * same IDs returned in the response of the preceding {@code /generate} call.
 * Keeping them on the request (instead of in a server-side field) makes the
 * rollback stateless and safe under concurrent users.</p>
 */
public record RollbackRequest(
        List<String> eventIds
) {
}
