package sh4re_v2.sh4re_v2.repository.main;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sh4re_v2.sh4re_v2.domain.main.UserRefreshToken;

public interface RefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {
  Optional<UserRefreshToken> findByUsername(String username);
  void deleteByUsername(String username);
}