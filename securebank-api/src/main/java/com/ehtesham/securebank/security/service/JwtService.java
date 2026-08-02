package com.ehtesham.securebank.security.service;

import com.ehtesham.securebank.common.enums.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

/*
 * Switched from HS256 (one shared secret, used to both sign and verify)
 * to RS256: securebank-api is the only service that holds the PRIVATE
 * key and can mint tokens; every service (including this one, for
 * consistency) verifies using only the PUBLIC key. A compromised
 * downstream service can no longer forge a token for another user/role —
 * it can, at most, read and verify tokens it's handed, the same as before.
 *
 * jwt.private-key / jwt.public-key are base64-encoded PEM (the whole
 * "-----BEGIN...-----" block, base64'd as one blob so it survives being a
 * single-line env var). Generate a pair with:
 *   openssl genrsa -out private.pem 2048
 *   openssl pkcs8 -topk8 -inform PEM -in private.pem -out private_pkcs8.pem -nocrypt
 *   openssl rsa -in private.pem -pubout -out public.pem
 *   base64 -w0 private_pkcs8.pem   # -> JWT_PRIVATE_KEY (securebank-api only)
 *   base64 -w0 public.pem          # -> JWT_PUBLIC_KEY (every service)
 */
@Service
public class JwtService {

    @Value("${jwt.private-key}")
    private String privateKeyBase64Pem;

    @Value("${jwt.public-key}")
    private String publicKeyBase64Pem;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        this.privateKey = loadPrivateKey(privateKeyBase64Pem);
        this.publicKey = loadPublicKey(publicKeyBase64Pem);
    }

    // role embedded in token at generation
    public String generateToken(String email, String role, Long userId, UserStatus userStatus) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)// ← embed role
                .claim("userId", userId.toString())
                .claim("userStatus", userStatus.name())
                .issuedAt(new Date())
                .expiration(new Date(
                        System.currentTimeMillis() + jwtExpiration))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims ->
                claims.get("role", String.class));
    }

    // validates signature + expiry, no UserDetails needed
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration)
                .before(new Date());
    }

    public <T> T extractClaim(String token,
                              Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }

    private PrivateKey loadPrivateKey(String base64Pem) {
        try {
            byte[] der = stripPemToDer(base64Pem);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load jwt.private-key — check it's a "
                            + "base64-encoded PKCS8 PEM private key.", e);
        }
    }

    private PublicKey loadPublicKey(String base64Pem) {
        try {
            byte[] der = stripPemToDer(base64Pem);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load jwt.public-key — check it's a "
                            + "base64-encoded X.509 PEM public key.", e);
        }
    }

    private byte[] stripPemToDer(String base64Pem) {
        String pem = new String(
                Base64.getDecoder().decode(base64Pem), StandardCharsets.UTF_8);
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }
}
