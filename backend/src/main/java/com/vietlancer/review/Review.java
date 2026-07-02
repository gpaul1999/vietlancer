package com.vietlancer.review;

import com.vietlancer.job.Job;
import com.vietlancer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "reviewer_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User reviewee;

    /** 1..5 sao. */
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 2000)
    private String comment;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
