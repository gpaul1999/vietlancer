package com.vietlancer.bid;

import com.vietlancer.chat.ChatService;
import com.vietlancer.common.ApiException;
import com.vietlancer.job.Job;
import com.vietlancer.job.JobRepository;
import com.vietlancer.subscription.SubscriptionService;
import com.vietlancer.user.Role;
import com.vietlancer.user.User;
import com.vietlancer.wallet.WalletService;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BidService {

    private final BidRepository bidRepository;
    private final JobRepository jobRepository;
    private final WalletService walletService;
    private final SubscriptionService subscriptionService;
    private final ChatService chatService;

    @Value("${app.platform.free-bids-per-month}")
    private int freeBidsPerMonth;

    public record PlaceBidRequest(BigDecimal amount, Integer deliveryDays, String coverLetter) {}

    @Transactional
    public Bid place(User freelancer, Long jobId, PlaceBidRequest request) {
        if (freelancer.getRole() != Role.FREELANCER) {
            throw ApiException.forbidden("Chỉ freelancer mới được chào giá");
        }
        var job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy job"));
        if (job.getStatus() != Job.Status.OPEN) {
            throw ApiException.badRequest("Job không còn nhận chào giá");
        }
        if (bidRepository.existsByJobIdAndFreelancerId(jobId, freelancer.getId())) {
            throw ApiException.conflict("Bạn đã chào giá cho job này rồi");
        }
        enforceMonthlyLimit(freelancer);

        return bidRepository.save(Bid.builder()
                .job(job)
                .freelancer(freelancer)
                .amount(request.amount())
                .deliveryDays(request.deliveryDays())
                .coverLetter(request.coverLetter())
                .build());
    }

    /**
     * Client chấp nhận bid: giữ tiền vào escrow, gán freelancer cho job,
     * từ chối các bid còn lại và mở hội thoại giữa hai bên.
     */
    @Transactional
    public Bid accept(User client, Long bidId) {
        var bid = bidRepository.findById(bidId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bid"));
        var job = bid.getJob();
        if (!job.getClient().getId().equals(client.getId())) {
            throw ApiException.forbidden("Bạn không phải chủ job này");
        }
        if (job.getStatus() != Job.Status.OPEN) {
            throw ApiException.badRequest("Job không ở trạng thái nhận bid");
        }

        walletService.holdEscrow(client, bid.getAmount(),
                "Escrow cho job #%d: %s".formatted(job.getId(), job.getTitle()));

        bid.setStatus(Bid.Status.ACCEPTED);
        bidRepository.findByJobIdAndStatus(job.getId(), Bid.Status.PENDING).stream()
                .filter(other -> !other.getId().equals(bid.getId()))
                .forEach(other -> other.setStatus(Bid.Status.REJECTED));

        job.setStatus(Job.Status.IN_PROGRESS);
        job.setAssignedFreelancer(bid.getFreelancer());
        job.setEscrowAmount(bid.getAmount());
        jobRepository.save(job);

        chatService.openForAcceptedBid(job, bid.getFreelancer());
        return bidRepository.save(bid);
    }

    /** Freelancer thường: tối đa N bid/tháng. Premium: không giới hạn. */
    private void enforceMonthlyLimit(User freelancer) {
        if (subscriptionService.isPremium(freelancer.getId())) {
            return;
        }
        var startOfMonth = ZonedDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .toInstant();
        var used = bidRepository.countByFreelancerIdAndCreatedAtAfter(freelancer.getId(), startOfMonth);
        if (used >= freeBidsPerMonth) {
            throw ApiException.forbidden(
                    "Bạn đã dùng hết %d lượt chào giá miễn phí trong tháng. Nâng cấp Premium để chào giá không giới hạn."
                            .formatted(freeBidsPerMonth));
        }
    }
}
