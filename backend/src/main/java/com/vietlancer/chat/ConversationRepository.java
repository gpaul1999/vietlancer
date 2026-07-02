package com.vietlancer.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByJobIdAndClientIdAndFreelancerId(Long jobId, Long clientId, Long freelancerId);

    @Query("""
            select c from Conversation c
            where c.client.id = :userId or c.freelancer.id = :userId
            order by c.createdAt desc
            """)
    List<Conversation> findAllForUser(@Param("userId") Long userId);
}
