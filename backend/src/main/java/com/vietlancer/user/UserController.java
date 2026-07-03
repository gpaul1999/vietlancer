package com.vietlancer.user;

import com.vietlancer.common.ApiException;
import com.vietlancer.review.ReviewRepository;
import com.vietlancer.subscription.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
            String avatarUrl, boolean premium, boolean verified, Double ratingAvg, long ratingCount) {}

    public record FreelancerSearchResult(
            List<FreelancerCard> freelancers, int page, int totalPages, long totalElements) {}

    /** Danh bạ freelancer: tìm theo tên / kỹ năng / bio. Premium xếp trước (ORDER BY trong DB). */
    @GetMapping("/freelancers")
    public FreelancerSearchResult freelancers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50));
        var normalizedQ = q == null || q.isBlank() ? null : q.trim();
        var result = userRepository.searchByRole(Role.FREELANCER, normalizedQ, Instant.now(), pageable);

        // Batch 2 query cho cả trang (premium + rating) thay vì 3 query mỗi card
        var userIds = result.getContent().stream().map(User::getId).toList();
        var premiumIds = subscriptionService.premiumUserIds(userIds);
        var ratings = reviewRepository.ratingSummaries(userIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        var cards = result.getContent().stream()
                .map(u -> {
                    var rating = ratings.get(u.getId());
                    return new FreelancerCard(
                            u.getId(),
                            u.getFullName(),
                            u.getBio(),
                            UserDto.skillsOf(u),
                            u.getHourlyRate(),
                            u.getAvatarUrl(),
                            premiumIds.contains(u.getId()),
                            u.getKycStatus() == User.KycStatus.VERIFIED,
                            rating == null ? null : (Double) rating[1],
                            rating == null ? 0L : (Long) rating[2]);
                })
                .toList();
        return new FreelancerSearchResult(cards, page, result.getTotalPages(), result.getTotalElements());
    }

    public record UpdateProfileRequest(
            String fullName,
            @Size(max = 2000) String bio,
            List<String> skills,
            BigDecimal hourlyRate,
            String avatarUrl) {}

    /** Hồ sơ công khai — không lộ email (endpoint mở cho khách vãng lai). */
    @GetMapping("/{id}")
    public PublicUserDto get(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(PublicUserDto::from)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    public record KycRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Pattern(regexp = "\\d{9,12}", message = "Số CCCD/CMND phải gồm 9-12 chữ số")
            String idNumber) {}

    /** Nộp hồ sơ xác minh danh tính (KYC) — admin sẽ duyệt thủ công. */
    @org.springframework.web.bind.annotation.PostMapping("/me/kyc")
    public UserDto submitKyc(@AuthenticationPrincipal User user, @Valid @RequestBody KycRequest request) {
        if (user.getKycStatus() == User.KycStatus.VERIFIED) {
            throw ApiException.badRequest("Tài khoản đã được xác minh");
        }
        if (user.getKycStatus() == User.KycStatus.PENDING) {
            throw ApiException.conflict("Hồ sơ đang chờ duyệt");
        }
        user.setKycIdNumber(request.idNumber());
        user.setKycStatus(User.KycStatus.PENDING);
        user.setKycNote(null);
        return UserDto.from(userRepository.save(user));
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
