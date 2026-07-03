package com.vietlancer.chat;

import com.vietlancer.common.ApiException;
import com.vietlancer.job.JobService;
import com.vietlancer.user.User;
import com.vietlancer.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final JobService jobService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public record StartRequest(@NotNull Long jobId, @NotNull Long freelancerId) {}

    public record MessageBody(@NotBlank @Size(max = 4000) String content) {}

    public record ParticipantDto(Long id, String fullName, String avatarUrl) {}

    public record ConversationDto(
            Long id, Long jobId, String jobTitle,
            ParticipantDto client, ParticipantDto freelancer,
            String lastMessage, Instant createdAt) {}

    public record MessageDto(Long id, Long senderId, String content, Instant createdAt) {}

    @PostMapping("/start")
    public ConversationDto start(@AuthenticationPrincipal User user, @Valid @RequestBody StartRequest request) {
        var job = jobService.find(request.jobId());
        var freelancer = userRepository.findById(request.freelancerId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy freelancer"));
        var conversation = chatService.open(user, job, freelancer);
        var lastMessage = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(Message::getContent)
                .orElse(null);
        return toDto(conversation, lastMessage);
    }

    @GetMapping
    public List<ConversationDto> myConversations(@AuthenticationPrincipal User user) {
        var conversations = conversationRepository.findAllForUser(user.getId());
        // 1 query lấy tin nhắn cuối cho cả danh sách — tránh N+1
        var ids = conversations.stream().map(Conversation::getId).toList();
        var lastMessages = ids.isEmpty()
                ? java.util.Map.<Long, String>of()
                : messageRepository.lastMessagesFor(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                m -> m.getConversation().getId(), Message::getContent));
        return conversations.stream()
                .map(c -> toDto(c, lastMessages.get(c.getId())))
                .toList();
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@AuthenticationPrincipal User user, @PathVariable Long id) {
        var conversation = chatService.find(id);
        chatService.requireParticipant(user, conversation);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(id).stream()
                .map(m -> new MessageDto(m.getId(), m.getSender().getId(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{id}/messages")
    public MessageDto send(
            @AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody MessageBody body) {
        var message = chatService.send(user, id, body.content());
        var dto = new MessageDto(message.getId(), message.getSender().getId(), message.getContent(),
                message.getCreatedAt());
        // Realtime: đẩy tin nhắn tới người đang mở hội thoại (subscriber đã được
        // StompAuthInterceptor xác minh là participant)
        messagingTemplate.convertAndSend("/topic/conversations/" + id, dto);
        return dto;
    }

    private ConversationDto toDto(Conversation c, String lastMessage) {
        return new ConversationDto(
                c.getId(),
                c.getJob().getId(),
                c.getJob().getTitle(),
                new ParticipantDto(c.getClient().getId(), c.getClient().getFullName(), c.getClient().getAvatarUrl()),
                new ParticipantDto(c.getFreelancer().getId(), c.getFreelancer().getFullName(),
                        c.getFreelancer().getAvatarUrl()),
                lastMessage,
                c.getCreatedAt());
    }
}
