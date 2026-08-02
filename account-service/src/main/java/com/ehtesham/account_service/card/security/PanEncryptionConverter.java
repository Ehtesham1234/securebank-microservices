package com.ehtesham.account_service.card.security;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * C5 fix: card_number (the PAN) was previously stored in plaintext. This
 * converter transparently encrypts it with AES-256-GCM before every write
 * and decrypts it on every read, so the database (and any backup/dump of
 * it) never holds the raw PAN.
 *
 * Registered explicitly via @Convert(converter = PanEncryptionConverter.class)
 * on Card.cardNumber rather than autoApply — this should never silently
 * apply to some other String field.
 *
 * Depends on Spring Boot's automatic registration of @Component-annotated
 * JPA converters with Hibernate (via SpringBeanContainer), which is on by
 * default when using spring-boot-starter-data-jpa — this is what lets
 * @Value be injected into a converter that Hibernate itself instantiates.
 */
@Component
@Converter(autoApply = false)
public class PanEncryptionConverter
        implements AttributeConverter<String, String> {

    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Base64-encoded 256-bit (32-byte) AES key. Generate one with:
    //   openssl rand -base64 32
    @Value("${card.encryption.key}")
    private String base64Key;

    private SecretKeySpec secretKey;

    @PostConstruct
    public void init() {
        if (base64Key == null || base64Key.isBlank()
                || "change-me".equals(base64Key)) {
            throw new IllegalStateException(
                    "card.encryption.key is not configured. Generate one " +
                            "with `openssl rand -base64 32` and set it via " +
                            "the CARD_ENCRYPTION_KEY env var. Losing or " +
                            "rotating this key without a re-encryption " +
                            "migration will make all existing card numbers " +
                            "unreadable.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "card.encryption.key must be valid Base64.", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "card.encryption.key must decode to exactly 32 bytes " +
                            "(AES-256). Got " + keyBytes.length + " bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }

    @Override
    public String convertToDatabaseColumn(String plainPan) {
        if (plainPan == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(
                    plainPan.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCiphertext = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(ciphertext, 0, ivAndCiphertext, iv.length,
                    ciphertext.length);

            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to encrypt card number", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(storedValue);
            byte[] iv = Arrays.copyOfRange(
                    ivAndCiphertext, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(
                    ivAndCiphertext, GCM_IV_LENGTH_BYTES,
                    ivAndCiphertext.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt card number — wrong key, or data " +
                            "predates encryption being enabled?", e);
        }
    }
}
