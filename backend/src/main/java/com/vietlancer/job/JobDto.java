package com.vietlancer.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobDto(
        Long id,
        String title,
        String description,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        LocalDate deadline,
        Job.Status status,
        List<TopicRef> topics,
        String aiEngine,
        String aiExplanation,
        ClientRef client,
        Long assignedFreelancerId,
        BigDecimal escrowAmount,
        boolean milestoneBased,
        long bidCount,
        Instant createdAt) {

    public record TopicRef(String slug, String name, String icon) {}

    public record ClientRef(Long id, String fullName, String avatarUrl, boolean premium) {}

    public static JobDto from(Job job, long bidCount, boolean clientPremium) {
        return new JobDto(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getBudgetMin(),
                job.getBudgetMax(),
                job.getDeadline(),
                job.getStatus(),
                job.getTopics().stream()
                        .map(t -> new TopicRef(t.getSlug(), t.getName(), t.getIcon()))
                        .toList(),
                job.getAiEngine(),
                job.getAiExplanation(),
                new ClientRef(
                        job.getClient().getId(),
                        job.getClient().getFullName(),
                        job.getClient().getAvatarUrl(),
                        clientPremium),
                job.getAssignedFreelancer() == null ? null : job.getAssignedFreelancer().getId(),
                job.getEscrowAmount(),
                job.isMilestoneBased(),
                bidCount,
                job.getCreatedAt());
    }
}
