package com.example.productrepair.security;

import com.example.productrepair.entity.Role;
import com.example.productrepair.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final ConcurrentMap<String, Long> revokedTokens = new ConcurrentHashMap<>();

    public JwtService(@Value("${repaircare.jwt.secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] generatedSecret = new byte[32];
            new SecureRandom().nextBytes(generatedSecret);
            signingKey = Keys.hmacShaKeyFor(generatedSecret);
            logger.warn("REPAIRCARE_JWT_SECRET is not configured; tokens will be invalidated when the backend restarts.");
        } else {
            byte[] secretBytes = Decoders.BASE64.decode(configuredSecret);
            if (secretBytes.length < 32) {
                throw new IllegalArgumentException("REPAIRCARE_JWT_SECRET must decode to at least 32 bytes");
            }
            signingKey = Keys.hmacShaKeyFor(secretBytes);
        }
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(2, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String email = claims.getSubject();
        String roleClaim = claims.get("role", String.class);
        String tokenId = claims.getId();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        if (roleClaim == null || roleClaim.isBlank()) {
            throw new IllegalArgumentException("JWT role is missing");
        }
        if (tokenId == null || revokedTokens.containsKey(tokenId)) {
            throw new IllegalArgumentException("JWT has been revoked");
        }
        return new AuthenticatedUser(email, Role.valueOf(roleClaim));
    }

    public void revokeToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String tokenId = claims.getId();
        Date expiration = claims.getExpiration();
        if (tokenId != null && expiration != null && expiration.after(new Date())) {
            revokedTokens.put(tokenId, expiration.getTime());
        }
        long now = System.currentTimeMillis();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public record AuthenticatedUser(String email, Role role) {}
}
