package com.vietlancer.config;

import com.vietlancer.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var jwt = header.substring(7);
                var email = jwtService.extractEmail(jwt);
                var issuedAt = jwtService.extractIssuedAt(jwt);
                userRepository.findByEmail(email).filter(u -> isStillValid(u, issuedAt)).ifPresent(user -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            } catch (Exception ignored) {
                // Token không hợp lệ → tiếp tục như request ẩn danh
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Token phát hành trước lần đổi mật khẩu gần nhất bị coi là hết hiệu lực.
     * (JWT chỉ lưu issuedAt theo giây nên cắt passwordChangedAt về giây để so sánh cho khớp.)
     */
    private static boolean isStillValid(com.vietlancer.user.User user, java.time.Instant issuedAt) {
        var changedAt = user.getPasswordChangedAt();
        return changedAt == null
                || !issuedAt.isBefore(changedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }
}
