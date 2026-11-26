package com.landfall.common.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "landfall.pagination")
public record PaginationProperties(
        @DefaultValue("20") @Min(1) int defaultSize, @DefaultValue("100") @Min(1) int maxSize) {

    @AssertTrue(message = "landfall.pagination.default-size must not exceed max-size") public boolean isValidRange() {
        return defaultSize <= maxSize;
    }

    public PageQuery resolve(Integer page, Integer pageSize) {
        int resolvedPage = page == null ? 1 : page;
        if (resolvedPage < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "page 必须大于等于 1");
        }
        int resolvedSize = pageSize == null ? defaultSize : pageSize;
        if (resolvedSize < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "pageSize 必须大于等于 1");
        }
        // 按 docs/standards/api-contract.md 分页契约，超过可配置 maxSize 时静默钳位至 maxSize。
        return new PageQuery(resolvedPage, Math.min(resolvedSize, maxSize));
    }
}
