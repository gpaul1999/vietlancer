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

    /** Rating trung bình + số lượng theo lô user — tránh N+1 ở danh bạ freelancer. */
    @Query("""
            select r.reviewee.id, avg(r.rating), count(r) from Review r
            where r.reviewee.id in :userIds group by r.reviewee.id
            """)
    java.util.List<Object[]> ratingSummaries(@Param("userIds") java.util.Collection<Long> userIds);
}
