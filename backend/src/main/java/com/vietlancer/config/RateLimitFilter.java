package com.vietlancer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting theo cửa sổ 1 phút, đếm theo IP (fixed window, in-memory).
 * - /api/auth/**: giới hạn chặt để chống brute-force mật khẩu.
 * - /api/** còn lại: giới hạn rộng chống abuse.
 *
 * Đủ dùng cho single instance. Khi scale ngang nhiều instance → chuyển sang
 * Redis-based limiter (đã ghi trong PLAN.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT_PER_MINUTE = 20;
    private static final int API_LIMIT_PER_MINUTE = 300;

    private record Window(long startEpochMinute, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * CHỈ bật khi app đứng sau reverse proxy tin cậy (nginx/CDN) có ghi đè X-Forwarded-For.
     * Mặc định false: header do client tự gửi được → giả mạo để né rate limit.
     */
    @Value("${app.rate-limit.trust-forwarded-header:false}")
    private boolean trustForwardedHeader;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        var isAuth = path.startsWith("/api/auth/");
        var limit = isAuth ? AUTH_LIMIT_PER_MINUTE : API_LIMIT_PER_MINUTE;
        var key = (isAuth ? "auth:" : "api:") + clientIp(request);
        var nowMinute = System.currentTimeMillis() / 60_000;

        var window = windows.compute(key, (k, w) ->
                w == null || w.startEpochMinute() != nowMinute
                        ? new Window(nowMinute, new AtomicInteger())
                        : w);

        if (window.count().incrementAndGet() > limit) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Quá nhiều yêu cầu. Vui lòng thử lại sau ít phút.\"}");
            return;
        }

        // Dọn rác cơ hội: xóa các cửa sổ cũ khi map phình to
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().startEpochMinute() < nowMinute);
        }

        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedHeader) {
            var forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
