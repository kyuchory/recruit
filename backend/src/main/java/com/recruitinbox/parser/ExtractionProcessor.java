package com.recruitinbox.parser;

/**
 * Does the actual analysis for one claimed {@link ExtractionRun}. Must not hold
 * a DB transaction across its network calls; the worker owns claim + finalize.
 * Step 9 ships a stub; Step 10 replaces it with the HTML pipeline.
 */
public interface ExtractionProcessor {

    ProcessOutcome process(ExtractionRun run);
}
