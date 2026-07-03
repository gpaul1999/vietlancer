package com.vietlancer.topic;

import com.vietlancer.common.ApiException;
import com.vietlancer.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicRepository topicRepository;
    private final TopicFollowRepository topicFollowRepository;

    public record TopicDto(Long id, String slug, String name, String nameEn, String icon) {
        public static TopicDto from(Topic topic) {
            return new TopicDto(topic.getId(), topic.getSlug(), topic.getName(), topic.getNameEn(), topic.getIcon());
        }
    }

    @GetMapping
    public List<TopicDto> list() {
        return topicRepository.findAll(Sort.by("name")).stream().map(TopicDto::from).toList();
    }

    /** Danh sách slug topic mà user đang theo dõi (nhận job alert). */
    @GetMapping("/followed")
    public List<String> followed(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw ApiException.forbidden("Chưa đăng nhập");
        }
        return topicFollowRepository.findByUserId(user.getId()).stream()
                .map(f -> f.getTopic().getSlug())
                .toList();
    }

    @PostMapping("/{slug}/follow")
    @Transactional
    public void follow(@AuthenticationPrincipal User user, @PathVariable String slug) {
        var topic = topicRepository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy topic"));
        if (!topicFollowRepository.existsByUserIdAndTopicId(user.getId(), topic.getId())) {
            topicFollowRepository.save(TopicFollow.builder().user(user).topic(topic).build());
        }
    }

    @DeleteMapping("/{slug}/follow")
    @Transactional
    public void unfollow(@AuthenticationPrincipal User user, @PathVariable String slug) {
        var topic = topicRepository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy topic"));
        topicFollowRepository.deleteByUserIdAndTopicId(user.getId(), topic.getId());
    }
}
