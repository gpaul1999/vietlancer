package com.vietlancer.milestone;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    /** Các mốc đang giữ tiền trong escrow. */
    List<Milestone.Status> HELD_STATUSES = List.of(Milestone.Status.FUNDED, Milestone.Status.SUBMITTED);

    List<Milestone> findByJobIdOrderByCreatedAtAsc(Long jobId);

    List<Milestone> findByJobIdAndStatusIn(Long jobId, List<Milestone.Status> statuses);

    boolean existsByJobIdAndStatusIn(Long jobId, List<Milestone.Status> statuses);

    @Query("""
            select coalesce(sum(m.amount), 0) from Milestone m
            where m.job.id = :jobId and m.status in :statuses
            """)
    BigDecimal sumAmountByJobIdAndStatusIn(
            @Param("jobId") Long jobId, @Param("statuses") List<Milestone.Status> statuses);

    /** Tổng tiền đang bị giữ trong escrow cho job (mốc FUNDED + SUBMITTED). */
    default BigDecimal heldAmountForJob(Long jobId) {
        return sumAmountByJobIdAndStatusIn(jobId, HELD_STATUSES);
    }
}
