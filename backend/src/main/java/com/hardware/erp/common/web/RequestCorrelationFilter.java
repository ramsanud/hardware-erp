package com.hardware.erp.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Attaches a correlation id to every request so a support call ("the invoice
 * screen failed at 3pm") can be traced through the logs. Accepts an inbound
 * X-Request-ID from a reverse proxy, otherwise generates one, and always
 * echoes it back on the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 36;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String requestId = request.getHeader(HEADER);
        // An inbound header is attacker-controlled: it lands in log files, so
        // it is length-capped and stripped of anything that could forge a line.
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_LENGTH
                || !requestId.matches("[A-Za-z0-9._-]+")) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        request.setAttribute(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String currentRequestId(HttpServletRequest request) {
        Object value = request.getAttribute(MDC_KEY);
        return value != null ? value.toString() : null;
    }
}
