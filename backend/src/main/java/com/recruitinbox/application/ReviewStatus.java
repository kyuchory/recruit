package com.recruitinbox.application;

/** {@code applications.review_status varchar(20) CHECK (... IN ('PENDING','CONFIRMED','NOT_REQUIRED'))}. */
public enum ReviewStatus {
    PENDING,
    CONFIRMED,
    NOT_REQUIRED
}
