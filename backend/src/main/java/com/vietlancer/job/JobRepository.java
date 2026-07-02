package com.vietlancer.job;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

    @Query("""
            select distinct j from Job j left join j.topics t
            where j.status = :status
              and (:topicSlug is null or t.slug = :topicSlug)
              and (:q is null
                   or lower(j.title) like lower(concat('%', :q, '%'))
                   or lower(j.description) like lower(concat('%', :q, '%')))
            """)
    Page<Job> search(
            @Param("status") Job.Status status,
            @Param("topicSlug") String topicSlug,
            @Param("q") String q,
            Pageable pageable);

    @Query("""
            select distinct j from Job j join j.topics t
            where j.status = :status
              and t.slug in :slugs
              and j.id not in (select b.job.id from com.vietlancer.bid.Bid b where b.freelancer.id = :freelancerId)
            order by j.createdAt desc
            """)
    List<Job> findOpenByTopicsExcludingBidder(
            @Param("status") Job.Status status,
            @Param("slugs") List<String> slugs,
            @Param("freelancerId") Long freelancerId,
            Pageable pageable);

    List<Job> findByClientIdOrderByCreatedAtDesc(Long clientId);

    List<Job> findByAssignedFreelancerIdOrderByCreatedAtDesc(Long freelancerId);
}
