package com.recruitinbox.parser.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Rule-only extraction (no AI): JSON-LD {@code JobPosting} first, then
 * OpenGraph/&lt;title&gt;, then a date scan near deadline labels. Produces a
 * {@code job.v1.1}-shaped candidate map with evidence; empty/low-signal input
 * yields {@code quality:"missing"} rather than a guess.
 */
@Component
public class JobHtmlExtractor {

    private static final Pattern DEADLINE_LABEL = Pattern.compile(
            "(마감|접수\\s*마감|원서\\s*접수|지원\\s*마감|apply\\s*by|deadline|due)", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public JobHtmlExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> extract(String html, String finalUrl) {
        Document doc = Jsoup.parse(html, finalUrl == null ? "" : finalUrl);
        List<String> warnings = new ArrayList<>();

        JsonNode jobPosting = findJobPosting(doc);

        Map<String, Object> company = candidate(companyFromJsonLd(jobPosting), "json_ld",
                "hiringOrganization.name");
        if (company.get("value") == null) {
            String og = meta(doc, "og:site_name");
            company = candidate(og, "dom", "meta[og:site_name]");
        }

        String title = jsonText(jobPosting, "title");
        String titleSource = "json_ld";
        String titleLocator = "title";
        if (title == null) {
            title = meta(doc, "og:title");
            titleSource = "dom";
            titleLocator = "meta[og:title]";
        }
        if (title == null) {
            Element t = doc.selectFirst("title");
            title = t == null ? null : t.text();
            titleSource = "dom";
            titleLocator = "title";
        }

        List<Map<String, Object>> events = new ArrayList<>();
        DateHeuristics.Parsed deadline = findDeadline(doc, jobPosting, warnings);
        if (deadline != null && !"unknown".equals(deadline.kind())) {
            events.add(deadlineEvent(deadline));
        } else if (deadline != null) {
            warnings.addAll(deadline.warnings());
        }

        Map<String, Object> position = new LinkedHashMap<>();
        position.put("candidateId", "p1");
        position.put("title", candidate(title, titleSource, titleLocator));
        position.put("events", events);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "job.v1.1");
        result.put("companyName", company);
        result.put("positions", title == null && company.get("value") == null ? List.of() : List.of(position));
        result.put("finalUrl", finalUrl);
        result.put("warnings", warnings);
        return result;
    }

    // ---- JSON-LD -----------------------------------------------------------

    private JsonNode findJobPosting(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonNode node;
            try {
                node = objectMapper.readTree(script.data());
            } catch (RuntimeException e) {
                continue;
            }
            JsonNode found = scanForType(node);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JsonNode scanForType(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode f = scanForType(child);
                if (f != null) {
                    return f;
                }
            }
            return null;
        }
        if (node.isObject()) {
            JsonNode type = node.get("@type");
            if (type != null && type.asString("").equalsIgnoreCase("JobPosting")) {
                return node;
            }
            JsonNode graph = node.get("@graph");
            if (graph != null) {
                return scanForType(graph);
            }
        }
        return null;
    }

    private String companyFromJsonLd(JsonNode jobPosting) {
        if (jobPosting == null) {
            return null;
        }
        JsonNode org = jobPosting.get("hiringOrganization");
        if (org == null) {
            return null;
        }
        if (org.isString()) {
            return blankToNull(org.asString());
        }
        JsonNode name = org.get("name");
        return name == null ? null : blankToNull(name.asString(""));
    }

    private String jsonText(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : blankToNull(v.asString(""));
    }

    // ---- dates -----------------------------------------------------------

    private DateHeuristics.Parsed findDeadline(Document doc, JsonNode jobPosting, List<String> warnings) {
        String validThrough = jsonText(jobPosting, "validThrough");
        if (validThrough != null) {
            DateHeuristics.Parsed p = DateHeuristics.parse(validThrough);
            if (!"unknown".equals(p.kind())) {
                return p;
            }
        }
        for (Element el : doc.select("li,td,p,span,div,dd,strong")) {
            String text = el.ownText();
            if (text.isBlank() || text.length() > 120) {
                continue;
            }
            if (DEADLINE_LABEL.matcher(text).find()) {
                DateHeuristics.Parsed p = DateHeuristics.parse(text);
                if (!"unknown".equals(p.kind()) || !p.warnings().isEmpty()) {
                    return p;
                }
            }
        }
        return null;
    }

    private Map<String, Object> deadlineEvent(DateHeuristics.Parsed p) {
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("kind", p.kind());
        schedule.put("scheduledDate", p.date() == null ? null : p.date().toString());
        schedule.put("scheduledAt", p.dateTime() == null ? null : p.dateTime().toString());
        schedule.put("startAt", null);
        schedule.put("endAt", null);
        schedule.put("timezone", "Asia/Seoul");
        schedule.put("timezoneAssumed", p.timezoneAssumed());
        schedule.put("rawText", p.rawText());

        Map<String, Object> scheduleCandidate = new LinkedHashMap<>();
        scheduleCandidate.put("value", schedule);
        scheduleCandidate.put("evidence", List.of(evidence("dom", p.rawText(), "deadline-scan")));
        scheduleCandidate.put("quality", "supported");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("candidateId", "p1-e1");
        event.put("type", "DOCUMENT_DEADLINE");
        event.put("customLabel", null);
        event.put("order", 0);
        event.put("schedule", scheduleCandidate);
        return event;
    }

    // ---- helpers -------------------------------------------------------

    private Map<String, Object> candidate(String value, String source, String locator) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("value", value);
        c.put("evidence", value == null ? List.of() : List.of(evidence(source, value, locator)));
        c.put("quality", value == null ? "missing" : "supported");
        return c;
    }

    private Map<String, Object> evidence(String source, String quote, String locator) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", source);
        e.put("quote", quote);
        e.put("locator", locator);
        e.put("assetId", null);
        return e;
    }

    private String meta(Document doc, String property) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        if (el == null) {
            el = doc.selectFirst("meta[name=" + property + "]");
        }
        return el == null ? null : blankToNull(el.attr("content"));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
