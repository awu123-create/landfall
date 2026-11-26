package com.landfall.common.web;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landfall.common.api.ApiResponse;
import com.landfall.common.api.BusinessException;
import com.landfall.common.api.ErrorCode;
import com.landfall.common.trace.TraceIdContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {PublicHealthController.class, PublicHealthAndExceptionContractTest.ProbeController.class})
@Import(PublicHealthAndExceptionContractTest.ProbeController.class)
@ExtendWith(OutputCaptureExtension.class)
class PublicHealthAndExceptionContractTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    PublicHealthAndExceptionContractTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void health_returnsStandardEnvelopeMatchesHeaderAndClearsMdc() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdContext.HEADER_NAME))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(
                result.getResponse().getHeader(TraceIdContext.HEADER_NAME),
                json.get("traceId").asText());
        Instant.parse(json.at("/data/timestamp").asText());
        assertNull(TraceIdContext.current());
    }

    @Test
    void traceId_reusesValidInboundHeaderAndReplacesInvalidHeader() throws Exception {
        mockMvc.perform(get("/api/v1/public/health").header(TraceIdContext.HEADER_NAME, "req_trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.HEADER_NAME, "req_trace-123"))
                .andExpect(jsonPath("$.traceId").value("req_trace-123"));

        String invalidHeader = "bad trace!@#";
        MvcResult rejected = mockMvc.perform(
                        get("/api/v1/public/health").header(TraceIdContext.HEADER_NAME, invalidHeader))
                .andExpect(status().isOk())
                .andReturn();

        assertNotEquals(invalidHeader, rejected.getResponse().getHeader(TraceIdContext.HEADER_NAME));
    }

    @Test
    void businessExceptions_map4xxWithoutErrorLogAndSanitize5xxWithSingleErrorLog(CapturedOutput output)
            throws Exception {
        mockMvc.perform(get("/api/v1/test-probe/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10400))
                .andExpect(jsonPath("$.message").value("业务拒绝示例"))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/api/v1/test-probe/business-default"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(10404))
                .andExpect(jsonPath("$.message").value(ErrorCode.NOT_FOUND.defaultMessage()));
        assertFalse(output.getAll().contains("ERROR"));

        String traceId = "trace-biz-500";
        MvcResult serverBiz = mockMvc.perform(
                        get("/api/v1/test-probe/business-500").header(TraceIdContext.HEADER_NAME, traceId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(10500))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andReturn();

        assertFalse(serverBiz.getResponse().getContentAsString().contains("secret-biz-detail"));
        long errorLines = output.getAll()
                .lines()
                .filter(line -> line.contains("ERROR [traceId=" + traceId + "]"))
                .count();
        assertEquals(1, errorLines);
    }

    @Test
    void requestValidationAndBindingFailures_return10400WithoutLoggingError(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/test-probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"landfall\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/test-probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10400))
                .andExpect(jsonPath("$.message", containsString("name")));

        mockMvc.perform(post("/api/v1/test-probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10400));

        mockMvc.perform(get("/api/v1/test-probe/query").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10400));

        mockMvc.perform(get("/api/v1/test-probe/query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10400));

        mockMvc.perform(get("/api/v1/test-probe/query").param("count", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10400));

        assertFalse(output.getAll().contains("ERROR"));
    }

    @Test
    void missingRouteAndWrongMethod_return10404And10405WithoutLoggingError(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/public/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(10404));

        mockMvc.perform(post("/api/v1/public/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(10405));

        assertFalse(output.getAll().contains("ERROR"));
    }

    @Test
    void unexpectedException_logsSingleMdcErrorAndSanitizesResponse(CapturedOutput output) throws Exception {
        String traceId = "trace-err-500";
        MvcResult result = mockMvc.perform(
                        get("/api/v1/test-probe/unexpected-error").header(TraceIdContext.HEADER_NAME, traceId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(10500))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("secret-internal-sql"));
        long errorLines = output.getAll()
                .lines()
                .filter(line ->
                        line.contains("ERROR [traceId=" + traceId + "]") && line.contains("Unhandled exception"))
                .count();
        assertEquals(1, errorLines);
    }

    @RestController
    @RequestMapping("/api/v1/test-probe")
    static class ProbeController {

        @GetMapping("/business-error")
        ApiResponse<Void> businessError() {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "业务拒绝示例");
        }

        @GetMapping("/business-default")
        ApiResponse<Void> businessDefault() {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        @GetMapping("/business-500")
        ApiResponse<Void> businessServerError() {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "secret-biz-detail");
        }

        @PostMapping("/validated")
        ApiResponse<Void> validated(@Valid @RequestBody ProbeReq req) {
            return ApiResponse.ok();
        }

        @GetMapping("/query")
        ApiResponse<Void> query(@RequestParam @Min(1) int count) {
            return ApiResponse.ok();
        }

        @GetMapping("/unexpected-error")
        ApiResponse<Void> unexpectedError() {
            throw new IllegalStateException("jdbc:postgresql://secret-internal-sql");
        }
    }

    record ProbeReq(@NotBlank String name) {}
}
