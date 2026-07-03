package com.vietlancer.job;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * Ưu tiên Premium ngay trong ORDER BY của DB — đúng trên mọi trang,
     * không sort lại trong bộ nhớ sau khi đã phân trang.
     */
    @Query("""
            select j from Job j
            where j.status = :status
              and (:topicSlug is null or exists (
                   select 1 from Job j2 join j2.topics t
                   where j2.id = j.id and t.slug = :topicSlug))
              and (:q is null
                   or lower(j.title) like lower(concat('%', :q, '%'))
                   or lower(j.description) like lower(concat('%', :q, '%')))
            order by case when exists (
                       select 1 from Subscription s
                       where s.user.id = j.client.id and s.expiresAt > :now)
                     then 0 else 1 end,
                     j.createdAt desc
            """)
    Page<Job> search(
            @Param("status") Job.Status status,
            @Param("topicSlug") String topicSlug,
            @Param("q") String q,
            @Param("now") java.time.Instant now,
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

    /** Số job đã hoàn thành theo lô freelancer — điểm kinh nghiệm cho AI matching. */
    @Query("""
            select j.assignedFreelancer.id, count(j) from Job j
            where j.status = :status and j.assignedFreelancer.id in :freelancerIds
            group by j.assignedFreelancer.id
            """)
    List<Object[]> completedCountsByFreelancerIds(
            @Param("status") Job.Status status,
            @Param("freelancerIds") java.util.Collection<Long> freelancerIds);

    List<Job> findByAssignedFreelancerIdOrderByCreatedAtDesc(Long freelancerId);
}
