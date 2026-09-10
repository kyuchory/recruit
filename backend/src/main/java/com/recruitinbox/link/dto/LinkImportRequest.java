package com.recruitinbox.link.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkImportRequest(
        @NotBlank @Size(max = 4096) String url) {
}
