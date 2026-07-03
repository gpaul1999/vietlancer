package com.vietlancer.chat;

import com.vietlancer.config.JwtService;
import com.vietlancer.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Bảo vệ kênh WebSocket:
 * - CONNECT: bắt buộc JWT hợp lệ (header Authorization) → gắn Principal cho phiên.
 * - SUBSCRIBE /topic/conversations/{id}: chỉ người tham gia hội thoại mới được nghe.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String CONVERSATION_TOPIC_PREFIX = "/topic/conversations/";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            var header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Thiếu JWT khi kết nối WebSocket");
            }
            try {
                var email = jwtService.extractEmail(header.substring(7));
                var user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new MessagingException("Người dùng không tồn tại"));
                accessor.setUser(new StompPrincipal(user.getEmail(), user.getId()));
            } catch (MessagingException e) {
                throw e;
            } catch (Exception e) {
                throw new MessagingException("JWT không hợp lệ");
            }
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            var destination = accessor.getDestination();
            if (destination != null && destination.startsWith(CONVERSATION_TOPIC_PREFIX)) {
                if (!(accessor.getUser() instanceof StompPrincipal principal)) {
                    throw new MessagingException("Chưa xác thực");
                }
                long conversationId;
                try {
                    conversationId = Long.parseLong(destination.substring(CONVERSATION_TOPIC_PREFIX.length()));
                } catch (NumberFormatException e) {
                    throw new MessagingException("Destination không hợp lệ");
                }
                var allowed = conversationRepository.findById(conversationId)
                        .map(c -> c.getClient().getId().equals(principal.userId())
                                || c.getFreelancer().getId().equals(principal.userId()))
                        .orElse(false);
                if (!allowed) {
                    throw new MessagingException("Bạn không thuộc hội thoại này");
                }
            }
        }
        return message;
    }
}
