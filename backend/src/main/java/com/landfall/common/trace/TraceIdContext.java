package com.landfall.common.trace;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class TraceIdContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private TraceIdContext() {}

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    static String resolveOrGenerate(String inbound) {
        if (inbound != null && VALID_TRACE_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
