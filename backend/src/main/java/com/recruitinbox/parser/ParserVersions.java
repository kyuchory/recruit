package com.recruitinbox.parser;

/** Version tags stamped on every {@link ExtractionRun} (v1.1 section 6.2). */
public final class ParserVersions {

    /** Extraction result JSON contract. */
    public static final String SCHEMA = "job.v1.1";
    /** Fetch + Jsoup + heuristics pipeline revision. */
    public static final String PARSER = "html-1";
    /** AI model configuration bundle; "none" until an AI adapter is wired. */
    public static final String MODEL_CONFIG = "none";

    private ParserVersions() {
    }
}
