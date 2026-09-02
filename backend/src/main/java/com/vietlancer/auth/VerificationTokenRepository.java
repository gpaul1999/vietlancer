package com.vietlancer.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHashAndType(String tokenHash, VerificationToken.Type type);

    /** Vô hiệu mọi token cùng loại của user (dùng khi phát hành token mới hoặc sau khi đổi mật khẩu). */
    @Modifying
    @Query("delete from VerificationToken t where t.user.id = :userId and t.type = :type")
    void deleteByUserIdAndType(@Param("userId") Long userId, @Param("type") VerificationToken.Type type);

    /** Dọn token đã hết hạn — chạy định kỳ để bảng không phình. */
    @Modifying
    @Query("delete from VerificationToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
