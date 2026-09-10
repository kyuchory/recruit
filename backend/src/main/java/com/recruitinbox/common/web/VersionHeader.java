package com.recruitinbox.common.web;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;

/** Parses an {@code If-Match} value (optionally quoted / weak) into a version long. */
public final class VersionHeader {

    private VersionHeader() {
    }

    public static long parse(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(ErrorCode.PRECONDITION_REQUIRED,
                    "If-Match header with the current version is required");
        }
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2).trim();
        }
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.PRECONDITION_FAILED, "malformed If-Match version");
        }
    }
}
