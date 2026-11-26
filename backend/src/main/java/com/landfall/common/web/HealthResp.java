package com.landfall.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record HealthResp(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp) {}
