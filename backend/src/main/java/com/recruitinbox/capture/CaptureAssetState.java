package com.recruitinbox.capture;

/** {@code capture_assets.state varchar(16)}; analysis is blocked until READY. */
public enum CaptureAssetState {
    PENDING,
    READY,
    REJECTED,
    DELETED
}
