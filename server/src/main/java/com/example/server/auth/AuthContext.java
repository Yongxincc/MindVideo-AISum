package com.example.server.auth;

public final class AuthContext {

    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthPrincipal get() {
        return CURRENT.get();
    }

    public static Long requireUserId() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null || principal.id() == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return principal.id();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
