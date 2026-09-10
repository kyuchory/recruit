package com.recruitinbox.parser;

import java.util.UUID;

/**
 * Post-commit "wake up" nudge for a freshly persisted {@link ExtractionRun}.
 * Only an optimization -- the DB poller (Step 9) is the source of truth and
 * finds the run regardless. A no-op implementation is used until Step 9.
 */
public interface ExtractionQueue {

    void signal(UUID runId);
}
