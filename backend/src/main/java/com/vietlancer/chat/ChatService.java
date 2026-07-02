package com.vietlancer.chat;

import com.vietlancer.common.ApiException;
import com.vietlancer.job.Job;
import com.vietlancer.subscription.SubscriptionService;
import com.vietlancer.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SubscriptionService subscriptionService;

    /**
     * LUẬT NHẮN TIN: client và freelancer chỉ được chat với nhau khi
     *   1) freelancer là người được client chọn (bid được chấp nhận), HOẶC
     *   2) CẢ HAI đều đang có gói Premium còn hạn.
     */
    public boolean canChat(Job job, User freelancer) {
        var isAssigned = job.getAssignedFreelancer() != null
                && job.getAssignedFreelancer().getId().equals(freelancer.getId());
        if (isAssigned) {
            return true;
        }
        return subscriptionService.isPremium(job.getClient().getId())
                && subscriptionService.isPremium(freelancer.getId());
    }

    /** Tạo hội thoại khi bid được chấp nhận (luôn hợp lệ). */
    @Transactional
    public Conversation openForAcceptedBid(Job job, User freelancer) {
        return getOrCreate(job, freelancer);
    }

    /** Mở hội thoại chủ động (trước khi chọn bid) — yêu cầu cả hai Premium. */
    @Transactional
    public Conversation open(User requester, Job job, User freelancer) {
        var isParticipant = requester.getId().equals(job.getClient().getId())
                || requester.getId().equals(freelancer.getId());
        if (!isParticipant) {
            throw ApiException.forbidden("Bạn không thuộc hội thoại này");
        }
        if (!canChat(job, freelancer)) {
            throw ApiException.forbidden(
                    "Chưa thể nhắn tin: chỉ được chat khi bid được chấp nhận, "
                    + "hoặc cả hai bên đều có gói Premium");
        }
        return getOrCreate(job, freelancer);
    }

    @Transactional
    public Message send(User sender, Long conversationId, String content) {
        var conversation = find(conversationId);
        requireParticipant(sender, conversation);
        // Kiểm tra lại quyền chat tại thời điểm gửi (Premium có thể đã hết hạn)
        if (!canChat(conversation.getJob(), conversation.getFreelancer())) {
            throw ApiException.forbidden("Quyền nhắn tin đã hết hiệu lực (gói Premium hết hạn?)");
        }
        return messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content)
                .build());
    }

    Conversation find(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội thoại"));
    }

    void requireParticipant(User user, Conversation conversation) {
        var isParticipant = conversation.getClient().getId().equals(user.getId())
                || conversation.getFreelancer().getId().equals(user.getId());
        if (!isParticipant) {
            throw ApiException.forbidden("Bạn không thuộc hội thoại này");
        }
    }

    private Conversation getOrCreate(Job job, User freelancer) {
        return conversationRepository
                .findByJobIdAndClientIdAndFreelancerId(job.getId(), job.getClient().getId(), freelancer.getId())
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .job(job)
                        .client(job.getClient())
                        .freelancer(freelancer)
                        .build()));
    }
}
