package com.recruitinbox.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.recruitinbox.common.error.ApiException;

class ProfileFieldCipherTest {
    @Test
    void encryptsWithRandomizedAesGcmAndDecrypts() {
        ProfileFieldCipher cipher = new ProfileFieldCipher(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        String first = cipher.encrypt("개인 연락처");
        String second = cipher.encrypt("개인 연락처");

        assertThat(first).startsWith("enc:v1:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("개인 연락처");
        assertThat(cipher.decrypt(second)).isEqualTo("개인 연락처");
    }

    @Test
    void refusesToStoreNonEmptyProfileDataWithoutAKey() {
        ProfileFieldCipher cipher = new ProfileFieldCipher("");

        assertThatThrownBy(() -> cipher.encrypt("개인 연락처"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PROFILE_ENCRYPTION_KEY");
        assertThat(cipher.encrypt("")).isEmpty();
    }
}
