package com.vietlancer.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Hồ sơ công khai — KHÔNG chứa email hay bất kỳ thông tin liên hệ riêng tư nào.
 * (UserDto đầy đủ chỉ trả cho chính chủ qua /api/auth/me.)
 */
public record PublicUserDto(
        Long id,
        String fullName,
        Role role,
        String bio,
        List<String> skills,
        BigDecimal hourlyRate,
        String avatarUrl,
        Instant createdAt) {

    public static PublicUserDto from(User user) {
        return new PublicUserDto(
                user.getId(),
                user.getFullName(),
                user.getRole(),
                user.getBio(),
                UserDto.skillsOf(user),
                user.getHourlyRate(),
                user.getAvatarUrl(),
                user.getCreatedAt());
    }
}
