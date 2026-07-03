package com.vietlancer.bid;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {

    /** Đếm bid theo lô job — tránh N+1 khi map danh sách JobDto. */
    @Query("select b.job.id, count(b) from Bid b where b.job.id in :jobIds group by b.job.id")
    List<Object[]> countByJobIds(@Param("jobIds") Collection<Long> jobIds);

    /** Giá bid theo topic (cho AI gợi ý giá). status null = mọi trạng thái. */
    @Query("""
            select b.amount from Bid b
            where (:status is null or b.status = :status)
              and exists (select 1 from Job j join j.topics t
                          where j.id = b.job.id and t.slug = :topicSlug)
            """)
    List<java.math.BigDecimal> amountsByTopic(
            @Param("topicSlug") String topicSlug, @Param("status") Bid.Status status);

    /** Phát hiện thư chào rập khuôn: freelancer dùng lại cùng nội dung ở nhiều bid. */
    long countByFreelancerIdAndCoverLetter(Long freelancerId, String coverLetter);

    List<Bid> findByJobIdOrderByCreatedAtDesc(Long jobId);

    List<Bid> findByFreelancerIdOrderByCreatedAtDesc(Long freelancerId);

    boolean existsByJobIdAndFreelancerId(Long jobId, Long freelancerId);

    long countByJobId(Long jobId);

    long countByFreelancerIdAndCreatedAtAfter(Long freelancerId, Instant after);

    List<Bid> findByJobIdAndStatus(Long jobId, Bid.Status status);
}
