package com.vietlancer.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId);

    boolean existsByJobIdAndReviewerId(Long jobId, Long reviewerId);

    @Query("select avg(r.rating) from Review r where r.reviewee.id = :userId")
    Double averageRating(@Param("userId") Long userId);

    long countByRevieweeId(Long revieweeId);
}
