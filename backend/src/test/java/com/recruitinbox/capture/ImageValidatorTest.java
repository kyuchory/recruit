package com.recruitinbox.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.recruitinbox.common.error.ApiException;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    private byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void acceptsRealPngAndReportsDimensions() throws Exception {
        var dim = validator.validate(png(12, 7), "image/png");
        assertThat(dim.mime()).isEqualTo("image/png");
        assertThat(dim.width()).isEqualTo(12);
        assertThat(dim.height()).isEqualTo(7);
    }

    @Test
    void rejectsSvgAndDeclaredMismatch() throws Exception {
        assertThatThrownBy(() -> validator.validate(
                "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(), "image/svg+xml"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validate(png(4, 4), "image/jpeg"))
                .isInstanceOf(ApiException.class);
    }
}
