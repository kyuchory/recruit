package com.recruitinbox.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Placeholder until Step 10. Produces an empty {@code job.v1.1}-shaped proposal
 * so the async pipeline (claim -> process -> finalize -> status) is exercisable
 * end to end without any network calls. Step 10's real processor is
 * {@code @Primary} and takes precedence.
 */
@Component
public class StubExtractionProcessor implements ExtractionProcessor {

    @Override
    public ProcessOutcome process(ExtractionRun run) {
        Map<String, Object> companyName = new HashMap<>();
        companyName.put("value", null);
        companyName.put("evidence", List.of());
        companyName.put("quality", "missing");

        Map<String, Object> result = new HashMap<>();
        result.put("schemaVersion", "job.v1.1");
        result.put("companyName", companyName);
        result.put("positions", List.of());
        result.put("warnings", List.of("STUB_PARSER_NO_ANALYSIS"));
        return ProcessOutcome.succeeded(result, List.of("STUB_PARSER_NO_ANALYSIS"));
    }
}
