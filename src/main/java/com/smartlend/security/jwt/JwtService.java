package com.smartlend.security.jwt;

import com.smartlend.model.entity.User;
import com.smartlend.service.RedisCacheService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Service for RSA-signed token generation and validation.
 * Uses RSA asymmetric keys for secure token signing.
 */
@Service
@Slf4j
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final RedisCacheService redisCacheService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Constructor loads RSA keys from configured paths.
     */
    public JwtService(
            @Value("${jwt.private-key}") Resource privateKeyResource,
            @Value("${jwt.public-key}") Resource publicKeyResource,
            RedisCacheService redisCacheService) throws IOException {

        this.redisCacheService = redisCacheService;
        this.privateKey = loadPrivateKey(privateKeyResource);
        this.publicKey = loadPublicKey(publicKeyResource);

        log.info("RSA keys loaded successfully");
    }

    /**
     * Generate Access Token.
     */
    public String generateToken(User user) {
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("tokenId", tokenId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(privateKey, Jwts.SIG.PS256)
                .compact();
    }

    /**
     * Generate Refresh Token.
     */
    public String generateRefreshToken(User user) {
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("tokenId", tokenId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(privateKey, Jwts.SIG.PS256)
                .compact();
    }

    /**
     * Extract User ID.
     */
    public Long extractUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /**
     * Extract Email.
     */
    public String extractEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    /**
     * Extract Role.
     */
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * Extract Token ID.
     */
    public String extractTokenId(String token) {
        return getClaims(token).get("tokenId", String.class);
    }

    /**
     * Check whether token is a Refresh Token.
     */
    public boolean isRefreshToken(String token) {
        return "refresh".equals(getClaims(token).get("type", String.class));
    }

    /**
     * Validate JWT.
     */
    public boolean validateToken(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        try {

            Claims claims = getClaims(token);

            String tokenId = claims.get("tokenId", String.class);

            if (redisCacheService.isJwtBlacklisted(tokenId)) {
                log.warn("Blacklisted JWT used.");
                return false;
            }

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("JWT expired.");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT.");
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT.");
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT is empty.");
        } catch (JwtException e) {
            log.warn("Invalid JWT.");
        }

        return false;
    }

    /**
     * Check expiration.
     */
    public boolean isTokenExpired(String token) {

        try {
            return getClaims(token)
                    .getExpiration()
                    .before(new Date());

        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Blacklist Access Token.
     */
    public void blacklistToken(String token) {

        if (token == null || token.isBlank()) {
            return;
        }

        try {

            Claims claims = getClaims(token);

            String tokenId = claims.get("tokenId", String.class);

            long ttl = claims.getExpiration().getTime()
                    - System.currentTimeMillis();

            if (ttl > 0) {
                redisCacheService.blacklistJwtToken(tokenId, ttl);
                log.info("Access token blacklisted.");
            }

        } catch (JwtException e) {
            log.warn("Unable to blacklist invalid token.");
        } catch (Exception e) {
            log.error("Failed to blacklist JWT.", e);
        }
    }

    /**
     * Parse Claims.
     */
    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Load RSA Private Key.
     */
    private PrivateKey loadPrivateKey(Resource resource) throws IOException {

        try {

            String key = new String(resource.getInputStream().readAllBytes())
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(key);

            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

            return KeyFactory.getInstance("RSA")
                    .generatePrivate(spec);

        } catch (Exception e) {
            throw new IOException("Failed to load private key.", e);
        }
    }

    /**
     * Load RSA Public Key.
     */
    private PublicKey loadPublicKey(Resource resource) throws IOException {

        try {

            String key = new String(resource.getInputStream().readAllBytes())
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(key);

            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

            return KeyFactory.getInstance("RSA")
                    .generatePublic(spec);

        } catch (Exception e) {
            throw new IOException("Failed to load public key.", e);
        }
    }
}