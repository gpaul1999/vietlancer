package com.vietlancer.topic;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicFollowRepository extends JpaRepository<TopicFollow, Long> {

    boolean existsByUserIdAndTopicId(Long userId, Long topicId);

    void deleteByUserIdAndTopicId(Long userId, Long topicId);

    List<TopicFollow> findByUserId(Long userId);

    /** Id người theo dõi bất kỳ topic nào trong danh sách (distinct) — cho job alert. */
    @Query("select distinct f.user.id from TopicFollow f where f.topic.id in :topicIds")
    List<Long> findFollowerIdsByTopicIds(@Param("topicIds") Collection<Long> topicIds);
}
