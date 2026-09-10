package com.recruitinbox.parser.html;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.recruitinbox.common.error.ApiException;

import tools.jackson.databind.json.JsonMapper;

class HtmlParsingUnitTest {

    private final JobHtmlExtractor extractor = new JobHtmlExtractor(JsonMapper.builder().build());

    // ---- UrlSafetyValidator ----

    @Test
    void ssrfBlocksNonPublicTargets() {
        UrlSafetyValidator v = new UrlSafetyValidator(false);
        assertThatThrownBy(() -> v.assertSafe(URI.create("http://127.0.0.1/x")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> v.assertSafe(URI.create("http://169.254.169.254/latest/meta-data")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> v.assertSafe(URI.create("http://10.1.2.3/x")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> v.assertSafe(URI.create("http://[::1]/x")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> v.assertSafe(URI.create("ftp://example.com/x")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> v.assertSafe(URI.create("http://example.com:8080/x")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void ssrfAllowsAPublicHttpsHost() {
        UrlSafetyValidator v = new UrlSafetyValidator(false);
        // example.com is IANA-reserved but resolves to public unicast addresses
        v.assertSafe(URI.create("https://example.com/jobs/1"));
    }

    // ---- DateHeuristics ----

    @Test
    void datesAreReadWithoutInventingYearsOrTimes() {
        assertThat(DateHeuristics.parse("2026-09-18T17:00:00+09:00").kind()).isEqualTo("exact");
        assertThat(DateHeuristics.parse("2026-09-18T17:00:00+09:00").dateTime()).isNotNull();

        var dateOnly = DateHeuristics.parse("접수 마감 2026.09.18");
        assertThat(dateOnly.kind()).isEqualTo("date_only");
        assertThat(dateOnly.date().toString()).isEqualTo("2026-09-18");
        assertThat(dateOnly.dateTime()).isNull();

        assertThat(DateHeuristics.parse("2026년 9월 18일").kind()).isEqualTo("date_only");

        var noYear = DateHeuristics.parse("9월 18일 마감");
        assertThat(noYear.kind()).isEqualTo("unknown");
        assertThat(noYear.warnings()).contains("YEAR_MISSING");

        assertThat(DateHeuristics.parse("상시채용").kind()).isEqualTo("rolling");
        assertThat(DateHeuristics.parse("추후 공지").kind()).isEqualTo("unknown");
    }

    // ---- JobHtmlExtractor ----

    @Test
    void extractsFromJsonLdJobPosting() {
        String html = """
                <html><head>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting",
                 "title":"백엔드 개발자 (신입)",
                 "hiringOrganization":{"@type":"Organization","name":"예시회사"},
                 "validThrough":"2026-09-18"}
                </script></head><body><h1>채용</h1></body></html>
                """;
        Map<String, Object> r = extractor.extract(html, "https://careers.example.com/jobs/1");

        assertThat(r).containsEntry("schemaVersion", "job.v1.1");
        assertThat(((Map<?, ?>) r.get("companyName")).get("value")).isEqualTo("예시회사");
        List<?> positions = (List<?>) r.get("positions");
        assertThat(positions).hasSize(1);
        Map<?, ?> pos = (Map<?, ?>) positions.get(0);
        assertThat(((Map<?, ?>) pos.get("title")).get("value")).isEqualTo("백엔드 개발자 (신입)");
        List<?> events = (List<?>) pos.get("events");
        assertThat(events).hasSize(1);
        Map<?, ?> ev = (Map<?, ?>) events.get(0);
        assertThat(ev.get("type")).isEqualTo("DOCUMENT_DEADLINE");
        Map<?, ?> schedule = (Map<?, ?>) ((Map<?, ?>) ev.get("schedule")).get("value");
        assertThat(schedule.get("kind")).isEqualTo("date_only");
        assertThat(schedule.get("scheduledDate")).isEqualTo("2026-09-18");
    }

    @Test
    void fallsBackToOpenGraphAndTitle() {
        String html = """
                <html><head>
                <meta property="og:site_name" content="OG회사">
                <meta property="og:title" content="프론트엔드 채용">
                <title>무시되는 타이틀</title></head><body>본문</body></html>
                """;
        Map<String, Object> r = extractor.extract(html, "https://x.example.com/j");
        assertThat(((Map<?, ?>) r.get("companyName")).get("value")).isEqualTo("OG회사");
        Map<?, ?> pos = (Map<?, ?>) ((List<?>) r.get("positions")).get(0);
        assertThat(((Map<?, ?>) pos.get("title")).get("value")).isEqualTo("프론트엔드 채용");
    }

    @Test
    void emptyPageYieldsNoPositions() {
        Map<String, Object> r = extractor.extract("<html><body></body></html>", "https://x.example.com/j");
        assertThat((List<?>) r.get("positions")).isEmpty();
        assertThat(((Map<?, ?>) r.get("companyName")).get("quality")).isEqualTo("missing");
    }
}
