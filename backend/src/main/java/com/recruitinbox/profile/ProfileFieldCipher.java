package com.recruitinbox.profile;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;

@Component
class ProfileFieldCipher {
    private static final String PREFIX = "enc:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    ProfileFieldCipher(@Value("${app.profile.encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null; // app may start, but profile values cannot be written until configured
            return;
        }
        byte[] decoded = Base64.getDecoder().decode(encodedKey);
        if (decoded.length != 32) throw new IllegalArgumentException("PROFILE_ENCRYPTION_KEY must be a base64 32-byte key");
        this.key = new SecretKeySpec(decoded, "AES");
    }

    String encrypt(String value) {
        String text = value == null ? "" : value;
        if (text.isEmpty()) return text;
        if (key == null) {
            throw new ApiException(ErrorCode.FEATURE_DISABLED,
                    "PROFILE_ENCRYPTION_KEY is required before storing personal profile data");
        }
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("profile field encryption failed", ex);
        }
    }

    String decrypt(String value) {
        if (value == null || value.isEmpty() || !value.startsWith(PREFIX)) return value == null ? "" : value;
        if (key == null) throw new IllegalStateException("PROFILE_ENCRYPTION_KEY is required to decrypt profile data");
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("profile field decryption failed", ex);
        }
    }
}
