package com.recruitinbox.parser.html;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based date reading (v1.1 section 8.4). Never invents a year or a time:
 * a bare {@code 9월 18일} stays {@code unknown} with a {@code YEAR_MISSING}
 * warning, {@code 2026.09.18} is {@code date_only} (no 23:59), and only an
 * explicit offset datetime becomes {@code exact}.
 */
public final class DateHeuristics {

    public record Parsed(String kind, LocalDate date, OffsetDateTime dateTime,
            boolean timezoneAssumed, String rawText, List<String> warnings) {
    }

    private static final Pattern ISO_OFFSET = Pattern.compile(
            "\\b(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(?::\\d{2})?(?:Z|[+-]\\d{2}:?\\d{2}))");
    private static final Pattern YMD = Pattern.compile(
            "\\b(20\\d{2})[.\\-/년\\s]{1,2}(\\d{1,2})[.\\-/월\\s]{1,2}(\\d{1,2})\\s*일?");
    private static final Pattern YMD_HM = Pattern.compile(
            "\\b(20\\d{2})[.\\-/년\\s]{1,2}(\\d{1,2})[.\\-/월\\s]{1,2}(\\d{1,2})\\s*일?\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern MD_ONLY = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[./월]\\s*(\\d{1,2})\\s*일?");
    private static final Pattern ROLLING = Pattern.compile("상시\\s*(채용|모집)|채용\\s*시\\s*(까지|마감)|수시\\s*채용");

    private DateHeuristics() {
    }

    public static Parsed parse(String raw) {
        List<String> warnings = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new Parsed("unknown", null, null, false, "", warnings);
        }
        String text = raw.trim();

        if (ROLLING.matcher(text).find()) {
            return new Parsed("rolling", null, null, false, text, warnings);
        }

        Matcher offset = ISO_OFFSET.matcher(text);
        if (offset.find()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(offset.group(1).replace(' ', 'T'));
                return new Parsed("exact", null, odt, false, offset.group(1), warnings);
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }

        Matcher ymdhm = YMD_HM.matcher(text);
        if (ymdhm.find()) {
            try {
                LocalDate d = LocalDate.of(Integer.parseInt(ymdhm.group(1)),
                        Integer.parseInt(ymdhm.group(2)), Integer.parseInt(ymdhm.group(3)));
                OffsetDateTime odt = d.atTime(Integer.parseInt(ymdhm.group(4)), Integer.parseInt(ymdhm.group(5)))
                        .atOffset(java.time.ZoneOffset.ofHours(9)); // KST assumed, flagged
                warnings.add("TIMEZONE_ASSUMED_ASIA_SEOUL");
                return new Parsed("exact", null, odt, true, ymdhm.group(), warnings);
            } catch (RuntimeException ignored) {
                // fall through
            }
        }

        Matcher ymd = YMD.matcher(text);
        if (ymd.find()) {
            try {
                LocalDate d = LocalDate.of(Integer.parseInt(ymd.group(1)),
                        Integer.parseInt(ymd.group(2)), Integer.parseInt(ymd.group(3)));
                return new Parsed("date_only", d, null, false, ymd.group(), warnings);
            } catch (RuntimeException ignored) {
                // fall through
            }
        }

        if (MD_ONLY.matcher(text).find()) {
            warnings.add("YEAR_MISSING");
            return new Parsed("unknown", null, null, false, text, warnings);
        }

        return new Parsed("unknown", null, null, false, text, warnings);
    }
}
