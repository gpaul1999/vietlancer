package com.vietlancer.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    /** Tin nhắn cuối của từng hội thoại theo lô — tránh N+1 ở danh sách hội thoại. */
    @org.springframework.data.jpa.repository.Query("""
            select m from Message m
            where m.id in (select max(m2.id) from Message m2
                           where m2.conversation.id in :conversationIds
                           group by m2.conversation.id)
            """)
    List<Message> lastMessagesFor(
            @org.springframework.data.repository.query.Param("conversationIds")
            java.util.Collection<Long> conversationIds);
}
