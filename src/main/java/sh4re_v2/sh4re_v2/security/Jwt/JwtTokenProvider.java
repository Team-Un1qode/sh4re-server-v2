package sh4re_v2.sh4re_v2.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import sh4re_v2.sh4re_v2.domain.main.User;
import sh4re_v2.sh4re_v2.exception.error_code.AuthStatusCode;
import sh4re_v2.sh4re_v2.exception.exception.ApplicationException;
import sh4re_v2.sh4re_v2.exception.exception.AuthException;
import sh4re_v2.sh4re_v2.security.TokenStatus;
import sh4re_v2.sh4re_v2.service.main.RefreshTokenService;
import sh4re_v2.sh4re_v2.service.main.UserService;

@Slf4j
@Component
public class JwtTokenProvider {

  private final RefreshTokenService refreshTokenService;
  private final UserService userService;
  @Value("${jwt.secret}")
  private String secretKey;

  @Value("${jwt.access-token-expiration}")
  private long accessTokenExpiration;

  @Value("${jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;

  public JwtTokenProvider(RefreshTokenService refreshTokenService, UserService userService) {
    this.refreshTokenService = refreshTokenService;
    this.userService = userService;
  }

  private Key getSigningKey() {
    byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateAccessToken(User user) {
    return generateToken(user, accessTokenExpiration, false);
  }

  public String generateRefreshToken(User user) {
    return generateToken(user, refreshTokenExpiration, true);
  }

  private String generateToken(User user, long expiration, boolean isRefreshToken) {
    long now = System.currentTimeMillis();
    Date issuedAt = new Date(now);
    Date expiryDate = new Date(now + expiration);

    return Jwts.builder()
        .setHeader(createHeader())
        .setClaims(isRefreshToken ? new HashMap<>() : createClaims(user))
        .setSubject(user.getUsername())
        .setIssuedAt(issuedAt)
        .setExpiration(expiryDate)
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)
        .compact();
  }

  private Map<String, Object> createHeader() {
    Map<String, Object> header = new HashMap<>();
    header.put("typ", "JWT");
    header.put("alg", "HS256");
    return header;
  }

  private Map<String, Object> createClaims(User user) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("id", user.getId());
    claims.put("role", user.getRole().getAuthority());
    claims.put("schoolId", user.getSchool().getId());
    return claims;
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public Long extractUserId(String token) {
    return extractAllClaims(token).get("id", Long.class);
  }

  public Long extractSchoolId(String token) {
    return extractAllClaims(token).get("schoolId", Long.class);
  }

  public Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  private Claims extractAllClaims(String token) {
    try {
      return Jwts.parserBuilder()
          .setSigningKey(getSigningKey())
          .build()
          .parseClaimsJws(token)
          .getBody();
    } catch (ExpiredJwtException e) {
      throw new ApplicationException(AuthStatusCode.EXPIRED_JWT, e);
    } catch (UnsupportedJwtException | MalformedJwtException | SignatureException | IllegalArgumentException e) {
      throw new ApplicationException(AuthStatusCode.INVALID_JWT, e);
    }
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
  }

  private boolean isTokenExpired(String token) {
    try {
      return extractExpiration(token).before(new Date());
    } catch (ExpiredJwtException e) {
      return true;
    }
  }

  public String getJwtFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }

  public String getRefreshTokenFromRequest(HttpServletRequest request) {
    String refreshToken = null;
    for (Cookie cookie : request.getCookies()) {
      if (cookie.getName().equals("refreshToken")) {
        refreshToken = cookie.getValue();
      }
    }
    if(refreshToken == null) throw AuthException.of(AuthStatusCode.INVALID_JWT);
    return refreshToken;
  }

  public TokenStatus validateToken(String token) {
    try {
      Jwts.parserBuilder()
          .setSigningKey(getSigningKey())
          .build()
          .parseClaimsJws(token);
      return TokenStatus.AUTHENTICATED;
    } catch (ExpiredJwtException e) {
      return TokenStatus.EXPIRED;
    } catch (UnsupportedJwtException | MalformedJwtException | SignatureException | IllegalArgumentException e) {
      return TokenStatus.INVALID;
    }
  }

  public void validateRefreshToken(String refreshToken) {
    if(validateToken(refreshToken) != TokenStatus.AUTHENTICATED) {
      throw AuthException.of(AuthStatusCode.INVALID_JWT);
    }

    String username = extractUsername(refreshToken);
    if (!refreshTokenService.isRefreshTokenValid(username, refreshToken)) {
      throw AuthException.of(AuthStatusCode.INVALID_JWT);
    }
  }

  public String generateNewRefreshToken(User user) {
    String newRefreshToken = generateRefreshToken(user);
    refreshTokenService.saveOrUpdateRefreshToken(user.getUsername(), newRefreshToken);
    return newRefreshToken;
  }
}
