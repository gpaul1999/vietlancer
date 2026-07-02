package com.vietlancer.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record UserDto(
        Long id,
        String email,
        String fullName,
        Role role,
        String bio,
        List<String> skills,
        BigDecimal hourlyRate,
        String avatarUrl,
        Instant createdAt) {

    public static UserDto from(User user) {
        var skills = user.getSkills() == null || user.getSkills().isBlank()
                ? List.<String>of()
                : Arrays.stream(user.getSkills().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getBio(),
                skills,
                user.getHourlyRate(),
                user.getAvatarUrl(),
                user.getCreatedAt());
    }
}
