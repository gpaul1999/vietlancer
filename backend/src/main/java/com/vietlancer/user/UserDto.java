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
        User.KycStatus kycStatus,
        String kycNote,
        Instant createdAt) {

    static List<String> skillsOf(User user) {
        return user.getSkills() == null || user.getSkills().isBlank()
                ? List.of()
                : Arrays.stream(user.getSkills().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static UserDto from(User user) {
        var skills = skillsOf(user);
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getBio(),
                skills,
                user.getHourlyRate(),
                user.getAvatarUrl(),
                user.getKycStatus(),
                user.getKycNote(),
                user.getCreatedAt());
    }
}
