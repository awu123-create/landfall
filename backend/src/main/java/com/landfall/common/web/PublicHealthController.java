package com.landfall.common.web;

import com.landfall.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "public-health", description = "公开健康检查")
@RestController
@RequestMapping("/api/v1/public")
public class PublicHealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public ApiResponse<HealthResp> health() {
        return ApiResponse.ok(new HealthResp("UP", Instant.now()));
    }
}
