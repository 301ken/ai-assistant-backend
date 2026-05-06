package com.ai.scheduler.dto.time;

import java.time.OffsetDateTime;

public record TimeRange(
        OffsetDateTime from,
        OffsetDateTime to
) {
}
