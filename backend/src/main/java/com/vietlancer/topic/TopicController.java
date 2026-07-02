package com.vietlancer.topic;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicRepository topicRepository;

    public record TopicDto(Long id, String slug, String name, String nameEn, String icon) {
        public static TopicDto from(Topic topic) {
            return new TopicDto(topic.getId(), topic.getSlug(), topic.getName(), topic.getNameEn(), topic.getIcon());
        }
    }

    @GetMapping
    public List<TopicDto> list() {
        return topicRepository.findAll(Sort.by("name")).stream().map(TopicDto::from).toList();
    }
}
