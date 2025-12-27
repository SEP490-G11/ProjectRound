package project.demo.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import project.demo.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, Instant now);
    List<RefreshToken> findAllByUserId(Long userId);
}
