package com.vietlancer.job;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    boolean existsByUserIdAndJobId(Long userId, Long jobId);

    void deleteByUserIdAndJobId(Long userId, Long jobId);

    List<SavedJob> findByUserIdOrderByCreatedAtDesc(Long userId);
}
