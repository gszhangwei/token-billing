package org.tw.token_billing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED,
            "Full authentication is required to access this resource"
        );
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle("Unauthorized");
        problemDetail.setInstance(URI.create("/api/usage"));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}
