package com.vietlancer.user;

import com.vietlancer.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    public record UpdateProfileRequest(
            String fullName,
            @Size(max = 2000) String bio,
            List<String> skills,
            BigDecimal hourlyRate,
            String avatarUrl) {}

    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(UserDto::from)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    @PutMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest request) {
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.skills() != null) {
            user.setSkills(String.join(",", request.skills()));
        }
        if (request.hourlyRate() != null) {
            user.setHourlyRate(request.hourlyRate());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        return UserDto.from(userRepository.save(user));
    }
}
