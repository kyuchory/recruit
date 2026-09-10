package com.recruitinbox.application;

/**
 * {@code applications.status varchar(20)}, design v1.1 section 4.
 * Result/other events never change this automatically (v1.1 section 4, 9.1).
 */
public enum ApplicationStatus {
    INTERESTED,
    PLANNED,
    APPLIED,
    IN_PROGRESS,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
