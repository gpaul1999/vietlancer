package com.vietlancer.bid;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByJobIdOrderByCreatedAtDesc(Long jobId);

    List<Bid> findByFreelancerIdOrderByCreatedAtDesc(Long freelancerId);

    boolean existsByJobIdAndFreelancerId(Long jobId, Long freelancerId);

    long countByJobId(Long jobId);

    long countByFreelancerIdAndCreatedAtAfter(Long freelancerId, Instant after);

    List<Bid> findByJobIdAndStatus(Long jobId, Bid.Status status);
}
