package com.dietbuilder.service;

import com.dietbuilder.model.ExpertSource;
import com.dietbuilder.repository.ExpertSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseScheduler {
    private final ExpertSourceRepository expertSourceRepository;
    private final ExpertKnowledgeService expertKnowledgeService;

    @Value("${rag.scheduler.source-stale-years:7}")
    private int sourceStaleYears;

    @Scheduled(cron = "${rag.scheduler.verify-cron:0 0 3 * * SUN}")
    public void verifySources() {
        List<ExpertSource> active = expertSourceRepository.findByActiveTrue();
        Instant now = Instant.now();
        int stale = 0;
        for (ExpertSource source : active) {
            source.setLastVerifiedAt(now);
            try {
                int year = Integer.parseInt(source.getPublicationDate().substring(0, 4));
                if (java.time.LocalDate.now().getYear() - year > sourceStaleYears) stale++;
            } catch (Exception ignored) {
            }
        }
        expertSourceRepository.saveAll(active);
        log.info("Knowledge base verification done. active={}, staleCandidates={}", active.size(), stale);
    }

    @Scheduled(cron = "${rag.scheduler.cache-cleanup-cron:0 0 */6 * * *}")
    public void cleanCache() {
        int evicted = expertKnowledgeService.evictExpiredQueryCacheEntries();
        log.info("Knowledge query cache cleanup done. evicted={}", evicted);
    }

    @Scheduled(cron = "${rag.scheduler.health-cron:0 0 8 * * *}")
    public void logHealthMetrics() {
        long total = expertSourceRepository.count();
        long active = expertSourceRepository.findByActiveTrue().size();
        long recentlyVerified = expertSourceRepository.findByActiveTrue().stream()
                .filter(s -> s.getLastVerifiedAt() != null && s.getLastVerifiedAt().isAfter(Instant.now().minus(30, ChronoUnit.DAYS)))
                .count();
        log.info("Knowledge base health: total={}, active={}, recentlyVerified={}", total, active, recentlyVerified);
    }
}
