package com.recruitinbox.user;

/** {@code users.state varchar(16) CHECK (state IN ('ACTIVE','DELETING'))}. */
public enum UserState {
    ACTIVE,
    DELETING
}
