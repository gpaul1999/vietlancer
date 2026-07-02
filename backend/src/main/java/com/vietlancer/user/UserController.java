package com.vietlancer.user;

import com.vietlancer.common.ApiException;
import com.vietlancer.review.ReviewRepository;
import com.vietlancer.subscription.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final SubscriptionService subscriptionService;

    public record FreelancerCard(
            Long id, String fullName, String bio, List<String> skills, BigDecimal hourlyRate,
            String avatarUrl, boolean premium, Double ratingAvg, long ratingCount) {}

    public record FreelancerSearchResult(
            List<FreelancerCard> freelancers, int page, int totalPages, long totalElements) {}

    /** Danh bạ freelancer: tìm theo tên / kỹ năng / bio. Premium hiển thị trước. */
    @GetMapping("/freelancers")
    public FreelancerSearchResult freelancers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        var normalizedQ = q == null || q.isBlank() ? null : q.trim();
        var result = userRepository.searchByRole(Role.FREELANCER, normalizedQ, pageable);
        var cards = result.getContent().stream()
                .map(u -> new FreelancerCard(
                        u.getId(),
                        u.getFullName(),
                        u.getBio(),
                        UserDto.from(u).skills(),
                        u.getHourlyRate(),
                        u.getAvatarUrl(),
                        subscriptionService.isPremium(u.getId()),
                        reviewRepository.averageRating(u.getId()),
                        reviewRepository.countByRevieweeId(u.getId())))
                .sorted((a, b) -> Boolean.compare(b.premium(), a.premium()))
                .toList();
        return new FreelancerSearchResult(cards, page, result.getTotalPages(), result.getTotalElements());
    }

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
