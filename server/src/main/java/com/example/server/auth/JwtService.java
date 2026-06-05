package com.example.server.auth;

import com.example.server.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    private final byte[] secret;
    private final long expireMillis;
    private final ObjectMapper objectMapper;

    public JwtService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.expire-days:7}") int expireDays,
            ObjectMapper objectMapper) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("auth.jwt.secret 未配置");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireMillis = expireDays * 24L * 60L * 60L * 1000L;
        this.objectMapper = objectMapper;
    }

    public String createToken(User user) {
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uid", user.getId());
        payload.put("username", user.getUsername());
        payload.put("role", user.getRole() != null ? user.getRole() : "USER");
        payload.put("iat", now);
        payload.put("exp", now + expireMillis);
        return sign(payload);
    }

    public AuthPrincipal parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("缺少登录凭证");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("登录凭证格式无效");
        }
        String payloadPart = parts[1];
        String signaturePart = parts[2];
        String expectedSignature = hmac(parts[0] + "." + payloadPart);
        if (!constantTimeEquals(signaturePart, expectedSignature)) {
            throw new UnauthorizedException("登录凭证签名校验失败");
        }

        try {
            byte[] jsonBytes = Base64.getUrlDecoder().decode(payloadPart);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(jsonBytes, Map.class);
            Number exp = (Number) payload.get("exp");
            if (exp == null || exp.longValue() < System.currentTimeMillis()) {
                throw new UnauthorizedException("登录已过期，请重新登录");
            }
            Number uid = (Number) payload.get("uid");
            if (uid == null) {
                throw new UnauthorizedException("登录凭证无效");
            }
            String username = String.valueOf(payload.get("username"));
            String role = payload.get("role") != null ? String.valueOf(payload.get("role")) : "USER";
            return new AuthPrincipal(uid.longValue(), username, role);
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("登录凭证解析失败");
        }
    }

    private String sign(Map<String, Object> payload) {
        try {
            String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
            String body = base64Url(objectMapper.writeValueAsString(payload));
            String content = header + "." + body;
            return content + "." + hmac(content);
        } catch (Exception e) {
            throw new IllegalStateException("生成 JWT 失败", e);
        }
    }

    private String hmac(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
