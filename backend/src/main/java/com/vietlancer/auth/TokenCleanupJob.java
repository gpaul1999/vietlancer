package com.vietlancer.auth;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Dọn token đã hết hạn mỗi ngày để bảng verification_tokens không phình theo thời gian. */
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final VerificationTokenRepository tokenRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        var deleted = tokenRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Đã dọn {} token hết hạn", deleted);
        }
    }
}
