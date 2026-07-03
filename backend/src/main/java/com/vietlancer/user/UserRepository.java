package com.vietlancer.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Premium xếp trước ngay trong ORDER BY — đúng trên mọi trang. */
    @Query("""
            select u from User u
            where u.role = :role
              and (:q is null
                   or lower(u.fullName) like lower(concat('%', :q, '%'))
                   or lower(u.skills) like lower(concat('%', :q, '%'))
                   or lower(u.bio) like lower(concat('%', :q, '%')))
            order by case when exists (
                       select 1 from Subscription s
                       where s.user.id = u.id and s.expiresAt > :now)
                     then 0 else 1 end,
                     u.createdAt desc
            """)
    Page<User> searchByRole(
            @Param("role") Role role, @Param("q") String q,
            @Param("now") java.time.Instant now, Pageable pageable);
}
