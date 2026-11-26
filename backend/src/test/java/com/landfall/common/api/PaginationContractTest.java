package com.landfall.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PaginationContractTest {

    private final PaginationProperties defaults = new PaginationProperties(20, 100);

    @ParameterizedTest
    @CsvSource(
            value = {
                "null, null, 1, 20, 0",
                "1, 10, 1, 10, 0",
                "2, 50, 2, 50, 50",
                "3, 100, 3, 100, 200",
                "3, 250, 3, 100, 200",
                "2, 12, 2, 12, 12"
            },
            nullValues = "null")
    void resolve_appliesDefaultsAndSilentlyClampsAboveMax(
            Integer rawPage, Integer rawPageSize, int expectedPage, int expectedPageSize, long expectedOffset) {
        PageQuery query = defaults.resolve(rawPage, rawPageSize);

        assertEquals(expectedPage, query.page());
        assertEquals(expectedPageSize, query.pageSize());
        assertEquals(expectedOffset, query.offset());
    }

    @Test
    void resolve_respectsCustomConfigurableLimit() {
        PaginationProperties custom = new PaginationProperties(15, 40);

        assertEquals(new PageQuery(1, 15), custom.resolve(null, null));
        assertEquals(new PageQuery(2, 40), custom.resolve(2, 60));
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "-1, 20", "1, 0", "1, -5"})
    void resolve_throwsBadRequestWhenPageOrPageSizeBelowOne(int page, int pageSize) {
        BusinessException ex = assertThrows(BusinessException.class, () -> defaults.resolve(page, pageSize));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @ParameterizedTest
    @CsvSource({"0, 100", "20, 0", "120, 100"})
    void paginationProperties_failsFastAtStartupWhenBoundsInvalid(int defaultSize, int maxSize) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(PaginationTestConfig.class);

        runner.withPropertyValues(
                        "landfall.pagination.default-size=" + defaultSize, "landfall.pagination.max-size=" + maxSize)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void paginationProperties_bindsValidOverridesFromSpringEnvironment() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(PaginationTestConfig.class);

        runner.withPropertyValues("landfall.pagination.default-size=15", "landfall.pagination.max-size=50")
                .run(context -> {
                    PaginationProperties props = context.getBean(PaginationProperties.class);
                    assertEquals(15, props.defaultSize());
                    assertEquals(50, props.maxSize());
                });
    }

    @Test
    void pageResult_computesHasMoreAllowsConfigurableSizeAndCopiesItems() {
        List<String> mutable = new ArrayList<>(List.of("a", "b"));
        PageResult<String> withMore = PageResult.of(mutable, new PageQuery(1, 250), 300);
        mutable.add("c");

        assertEquals(List.of("a", "b"), withMore.items());
        assertEquals(250, withMore.pageSize());
        assertTrue(withMore.hasMore());

        PageResult<String> exactEnd = PageResult.of(List.of("a"), new PageQuery(2, 20), 40);
        assertFalse(exactEnd.hasMore());
    }

    @Test
    void pageResultAndQuery_rejectBrokenInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 20));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(1, 0));
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(null, new PageQuery(1, 20), 0));
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), new PageQuery(1, 20), -1));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaginationProperties.class)
    static class PaginationTestConfig {}
}
