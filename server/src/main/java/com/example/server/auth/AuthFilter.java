package com.example.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/user/login",
            "/user/register"
    );

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            AuthPrincipal principal = jwtService.parseToken(token);
            AuthContext.set(principal);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            writeUnauthorized(response, e.getMessage());
        } finally {
            AuthContext.clear();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedException("Authorization 头格式无效");
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return token;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 401);
        body.put("msg", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
