package com.hardware.erp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.common.web.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Never names the missing permission - that maps the authorisation model
        // for an attacker.
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(
                "ACCESS_DENIED",
                "You do not have permission for this action",
                request.getRequestURI(),
                RequestCorrelationFilter.currentRequestId(request)));
    }
}
