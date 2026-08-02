package com.ehtesham.account_service.card.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Follow-up #5, Option A: a CVV must never be stored (C5) — but a real
 * card still needs one, and something has to be able to state "yes,
 * that's the right CVV for this card" without persisting it anywhere.
 *
 * Real card networks solve this by deriving the CVV deterministically
 * from PAN + expiry date, run through a keyed cryptographic function
 * under a bank-wide secret — nothing about the individual card is
 * stored, only one key. This does the same: HMAC-SHA256 over
 * "pan|expiryDate", truncated to 3 digits. Re-deriving with the same
 * inputs always produces the same CVV, so it can be shown to the
 * cardholder on demand (see CardServiceImpl.revealCvv) or checked
 * against a submitted value (verifyCvv) without ever storing it.
 *
 * Deliberately a SEPARATE key from CARD_ENCRYPTION_KEY (which encrypts
 * the PAN itself) — compromising one shouldn't compromise the other.
 */
@Component
public class CvvService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // Base64-encoded 256-bit (32-byte) key. Generate one with:
    //   openssl rand -base64 32
    @Value("${card.cvv.derivation-key}")
    private String base64Key;

    private SecretKeySpec secretKey;

    @PostConstruct
    public void init() {
        if (base64Key == null || base64Key.isBlank()
                || "change-me".equals(base64Key)) {
            throw new IllegalStateException(
                    "card.cvv.derivation-key is not configured. Generate "
                            + "one with `openssl rand -base64 32` and set "
                            + "it via the CVV_DERIVATION_KEY env var. This "
                            + "must be a DIFFERENT key from "
                            + "CARD_ENCRYPTION_KEY.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "card.cvv.derivation-key must be valid Base64.", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "card.cvv.derivation-key must decode to exactly 32 "
                            + "bytes. Got " + keyBytes.length + " bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    /**
     * Deterministically derives the 3-digit CVV for a card from its PAN
     * and expiry date. Same inputs always produce the same output — this
     * is what makes storing the CVV unnecessary.
     */
    public String derive(String pan, LocalDate expiryDate) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            String message = pan + "|" + expiryDate;
            byte[] digest = mac.doFinal(
                    message.getBytes(StandardCharsets.UTF_8));

            // Truncate to a positive 3-digit value the same way HOTP/TOTP
            // truncate an HMAC digest to a short numeric code.
            int truncated =
                    ((digest[0] & 0x7f) << 24)
                            | ((digest[1] & 0xff) << 16)
                            | ((digest[2] & 0xff) << 8)
                            | (digest[3] & 0xff);

            int cvv = truncated % 1000;
            return String.format("%03d", cvv);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to derive CVV", e);
        }
    }

    /**
     * Re-derives the CVV from the same inputs and compares against what
     * the user submitted — constant-time, so a failed guess doesn't leak
     * timing information about how close it was.
     */
    public boolean verify(String pan, LocalDate expiryDate,
                          String submittedCvv) {
        if (submittedCvv == null || submittedCvv.isBlank()) {
            return false;
        }
        String expected = derive(pan, expiryDate);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                submittedCvv.getBytes(StandardCharsets.UTF_8));
    }
}
