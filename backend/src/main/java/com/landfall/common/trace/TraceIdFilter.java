package com.landfall.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String existing = request.getAttribute(TraceIdContext.MDC_KEY) instanceof String s ? s : null;
        String traceId = existing != null
                ? existing
                : TraceIdContext.resolveOrGenerate(request.getHeader(TraceIdContext.HEADER_NAME));
        request.setAttribute(TraceIdContext.MDC_KEY, traceId);
        MDC.put(TraceIdContext.MDC_KEY, traceId);
        response.setHeader(TraceIdContext.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdContext.MDC_KEY);
        }
    }
}
