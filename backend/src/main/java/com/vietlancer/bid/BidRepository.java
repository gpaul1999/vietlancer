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

    List<Bid> findByJobIdOrderByCreatedAtDesc(Long jobId);

    List<Bid> findByFreelancerIdOrderByCreatedAtDesc(Long freelancerId);

    boolean existsByJobIdAndFreelancerId(Long jobId, Long freelancerId);

    long countByJobId(Long jobId);

    long countByFreelancerIdAndCreatedAtAfter(Long freelancerId, Instant after);

    List<Bid> findByJobIdAndStatus(Long jobId, Bid.Status status);
}
