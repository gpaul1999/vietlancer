package com.vietlancer.dispute;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    boolean existsByJobIdAndStatus(Long jobId, Dispute.Status status);

    List<Dispute> findByJobIdOrderByCreatedAtDesc(Long jobId);

    List<Dispute> findByStatusOrderByCreatedAtAsc(Dispute.Status status);

    @Query("""
            select d from Dispute d
            where d.job.client.id = :userId or d.job.assignedFreelancer.id = :userId
            order by d.createdAt desc
            """)
    List<Dispute> findAllForUser(@Param("userId") Long userId);
}
