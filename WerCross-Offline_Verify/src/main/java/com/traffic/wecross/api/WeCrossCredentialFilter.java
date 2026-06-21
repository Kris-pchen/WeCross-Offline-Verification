package com.traffic.wecross.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WeCrossCredentialFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final String routerUrl;

    public WeCrossCredentialFilter(
            ObjectMapper objectMapper,
            @Value("${wecross.router-url:http://127.0.0.1:8250}") String routerUrl) {
        this.objectMapper = objectMapper;
        this.routerUrl = routerUrl.replaceAll("/+$", "");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/verification/health".equals(request.getRequestURI())
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String credential = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (credential == null || credential.trim().isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Missing WeCross Authorization credential");
            return;
        }

        try {
            if (!verifyCredential(credential)) {
                writeError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired WeCross credential");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "wecross-webapp-user", credential, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            logger.warn("Unable to verify credential with WeCross Router " + routerUrl, e);
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify credential with WeCross Router");
        }
    }

    private boolean verifyCredential(String credential) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(routerUrl + "/auth/listAccount").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty(HttpHeaders.AUTHORIZATION, credential);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");

        byte[] requestBody = "{}".getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(requestBody);
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            return false;
        }
        InputStream stream = connection.getInputStream();
        if (stream == null) {
            return false;
        }

        try (InputStream responseBody = stream) {
            Map<String, Object> body =
                    objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            Object errorCode = body.get("errorCode");
            return status >= 200
                    && status < 300
                    && errorCode instanceof Number
                    && ((Number) errorCode).intValue() == 0;
        } finally {
            connection.disconnect();
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "FAILED");
        body.put("message", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}