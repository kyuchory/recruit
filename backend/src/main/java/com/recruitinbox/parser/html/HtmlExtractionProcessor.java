package com.recruitinbox.parser.html;

import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.recruitinbox.ai.AiProperties;
import com.recruitinbox.ai.AiUsageRecorder;
import com.recruitinbox.ai.TextExtractor;
import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.parser.ExtractionProcessor;
import com.recruitinbox.parser.ExtractionRun;
import com.recruitinbox.parser.ProcessOutcome;

/**
 * Rule-based URL analysis (Step 10, no AI): fetch under the SSRF policy, run
 * {@link JobHtmlExtractor}, and store the candidate proposal in
 * {@code extraction_runs.result}. Never writes to applications/events.
 * Step 11 chains an AI text pass here for the still-missing fields.
 */
@Component
@Primary
public class HtmlExtractionProcessor implements ExtractionProcessor {

    private static final Logger log = LoggerFactory.getLogger(HtmlExtractionProcessor.class);

    private final LinkRepository links;
    private final JobPageFetcher fetcher;
    private final JobHtmlExtractor extractor;
    private final TextExtractor textExtractor;
    private final AiUsageRecorder aiUsage;
    private final AiProperties aiProps;

    public HtmlExtractionProcessor(LinkRepository links, JobPageFetcher fetcher, JobHtmlExtractor extractor,
            TextExtractor textExtractor, AiUsageRecorder aiUsage, AiProperties aiProps) {
        this.links = links;
        this.fetcher = fetcher;
        this.extractor = extractor;
        this.textExtractor = textExtractor;
        this.aiUsage = aiUsage;
        this.aiProps = aiProps;
    }

    @Override
    public ProcessOutcome process(ExtractionRun run) {
        Link link = links.findByIdAndOwnerId(run.getLinkId(), run.getOwnerId()).orElse(null);
        if (link == null) {
            return ProcessOutcome.failed("LINK_GONE");
        }
        if (link.getOriginalUrl() == null || link.getOriginalUrl().isBlank()) {
            return ProcessOutcome.needsInput("NO_URL", List.of("NO_SOURCE_URL"));
        }

        JobPageFetcher.FetchResult fetched;
        try {
            fetched = fetcher.fetch(link.getNormalizedUrl() != null ? link.getNormalizedUrl() : link.getOriginalUrl());
        } catch (JobPageFetcher.FetchException e) {
            log.info("fetch failed for run {} ({}): {}", run.getId(), e.code(), e.getMessage());
            return switch (e.code()) {
                case "UNSAFE_URL" -> ProcessOutcome.failed("UNSAFE_URL");
                case "FETCH_BLOCKED", "FETCH_NOT_HTML" -> ProcessOutcome.needsInput(e.code(), List.of(e.code()));
                case "FETCH_TOO_LARGE" -> ProcessOutcome.needsInput("FETCH_TOO_LARGE", List.of("FETCH_TOO_LARGE"));
                default -> e.retryable() ? ProcessOutcome.retryable(e.code()) : ProcessOutcome.failed(e.code());
            };
        }

        String pageText = Jsoup.parse(fetched.html()).text();
        Map<String, Object> result = extractor.extract(fetched.html(), fetched.finalUrl());

        @SuppressWarnings("unchecked")
        List<Object> warnings = new java.util.ArrayList<>(
                (List<Object>) result.getOrDefault("warnings", List.of()));
        boolean noPositions = ((List<?>) result.getOrDefault("positions", List.of())).isEmpty();
        boolean companyMissing = "missing".equals(
                ((Map<?, ?>) result.getOrDefault("companyName", Map.of())).get("quality"));

        // AI text pass only for fields the rules could not resolve (v1.1 section 10.1).
        if (aiProps.enabled() && (noPositions || companyMissing) && !pageText.isBlank()) {
            List<String> missing = new java.util.ArrayList<>();
            if (companyMissing) {
                missing.add("companyName");
            }
            if (noPositions) {
                missing.add("positionTitle");
                missing.add("documentDeadline");
            }
            TextExtractor.Result ai = textExtractor.extract(new TextExtractor.Request(
                    run.getId(), run.getOwnerId(), link.getNormalizedUrl(), pageText, missing, "Asia/Seoul"));
            if (!ai.fields().isEmpty()) {
                result.put("aiFields", ai.fields());
            }
            warnings.addAll(ai.warnings());
            aiUsage.record(run.getOwnerId(), run.getId(), "text", ai.usage());
            noPositions = noPositions && !result.containsKey("aiFields");
        }

        result.put("warnings", warnings);
        if (noPositions && pageText.length() < 400 && !result.containsKey("aiFields")) {
            warnings.add("LOW_SIGNAL_PAGE");
            return ProcessOutcome.needsInput("JS_SHELL_OR_EMPTY", warnings);
        }
        return ProcessOutcome.succeeded(result, warnings);
    }
}
