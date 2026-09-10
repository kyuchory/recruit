package com.recruitinbox.parser.html;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.recruitinbox.link.Link;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.parser.ExtractionRun;
import com.recruitinbox.parser.ProcessOutcome;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class HtmlExtractionProcessorTest {

    @Mock
    LinkRepository links;

    private final JobHtmlExtractor extractor = new JobHtmlExtractor(JsonMapper.builder().build());

    private ExtractionRun run(UUID linkId, UUID owner) {
        ExtractionRun r = new ExtractionRun();
        r.setLinkId(linkId);
        r.setOwnerId(owner);
        return r;
    }

    private Link link(UUID owner, String url) {
        Link l = new Link();
        l.setOwnerId(owner);
        l.setOriginalUrl(url);
        l.setNormalizedUrl(url);
        return l;
    }

    private HtmlExtractionProcessor processor(JobPageFetcher fetcher) {
        return new HtmlExtractionProcessor(links, fetcher, extractor);
    }

    @Test
    void producesProposalFromFetchedHtml() {
        UUID owner = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        when(links.findByIdAndOwnerId(linkId, owner)).thenReturn(Optional.of(link(owner, "https://c.example.com/j/1")));

        JobPageFetcher fetcher = url -> new JobPageFetcher.FetchResult(url, """
                <html><head><script type="application/ld+json">
                {"@type":"JobPosting","title":"데이터 엔지니어","hiringOrganization":{"name":"콘텐츠사"},
                 "validThrough":"2026-10-01"}</script></head><body>본문이 충분히 길어야 합니다. 상세 설명 텍스트.</body></html>
                """, "text/html");

        ProcessOutcome out = processor(fetcher).process(run(linkId, owner));
        assertThat(out.kind()).isEqualTo(ProcessOutcome.Kind.SUCCEEDED);
        assertThat(out.result()).containsEntry("schemaVersion", "job.v1.1");
    }

    @Test
    void blockedFetchNeedsInput() {
        UUID owner = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        when(links.findByIdAndOwnerId(linkId, owner)).thenReturn(Optional.of(link(owner, "https://c.example.com/j/2")));
        JobPageFetcher fetcher = url -> {
            throw new JobPageFetcher.FetchException("FETCH_BLOCKED", false, "403");
        };
        assertThat(processor(fetcher).process(run(linkId, owner)).kind())
                .isEqualTo(ProcessOutcome.Kind.NEEDS_INPUT);
    }

    @Test
    void timeoutIsRetryableUnsafeIsFailed() {
        UUID owner = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        when(links.findByIdAndOwnerId(linkId, owner)).thenReturn(Optional.of(link(owner, "https://c.example.com/j/3")));

        JobPageFetcher timeout = url -> {
            throw new JobPageFetcher.FetchException("FETCH_TIMEOUT", true, "timeout");
        };
        assertThat(processor(timeout).process(run(linkId, owner)).kind())
                .isEqualTo(ProcessOutcome.Kind.RETRYABLE);

        JobPageFetcher unsafe = url -> {
            throw new JobPageFetcher.FetchException("UNSAFE_URL", false, "private ip");
        };
        assertThat(processor(unsafe).process(run(linkId, owner)).kind())
                .isEqualTo(ProcessOutcome.Kind.FAILED);
    }

    @Test
    void linkWithoutUrlNeedsInput() {
        UUID owner = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        Link l = new Link();
        l.setOwnerId(owner);
        when(links.findByIdAndOwnerId(linkId, owner)).thenReturn(Optional.of(l));
        JobPageFetcher fetcher = url -> {
            throw new AssertionError("must not fetch");
        };
        ProcessOutcome out = processor(fetcher).process(run(linkId, owner));
        assertThat(out.kind()).isEqualTo(ProcessOutcome.Kind.NEEDS_INPUT);
        assertThat(out.errorCode()).isEqualTo("NO_URL");
    }
}
