package sh4re_v2.sh4re_v2.service;

import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sh4re_v2.sh4re_v2.domain.UserRefreshToken;
import sh4re_v2.sh4re_v2.exception.error_code.AuthErrorCode;
import sh4re_v2.sh4re_v2.repository.RefreshTokenRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;

  @Value("${jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;

  public void saveOrUpdateRefreshToken(String username, String refreshToken) {
    Optional<UserRefreshToken> tokenOpt = refreshTokenRepository.findByUsername(username);
    long now = System.currentTimeMillis();
    Date expiryDate = new Date(now + refreshTokenExpiration);
    UserRefreshToken token;
    if (tokenOpt.isPresent()) {
      token = tokenOpt.get();
      token.setRefreshToken(refreshToken);
      token.setExpiryDate(expiryDate);
    } else {
      token = new UserRefreshToken(username, refreshToken, expiryDate);
    }
    refreshTokenRepository.save(token);
  }

  public boolean isRefreshTokenValid(String username, String refreshToken) {
    return refreshTokenRepository.findByUsername(username)
        .filter(t -> t.getRefreshToken().equals(refreshToken))
        .filter(t -> t.getExpiryDate().after(new Date()))
        .isPresent();
  }

  public void deleteByUsername(String username) {
    Optional<UserRefreshToken> tokenOpt = refreshTokenRepository.findByUsername(username);
    if(tokenOpt.isEmpty()) throw AuthErrorCode.ALREADY_LOGGED_OUT_USER.defaultException();
    else {
      refreshTokenRepository.deleteByUsername(username);
    }
  }
}