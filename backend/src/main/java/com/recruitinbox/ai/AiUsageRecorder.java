package com.recruitinbox.ai;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Appends one {@code usage_ledger} row per external model call (v1.1 section 10).
 * {@code reservation_key} is unique so a retry cannot double-count. Estimated
 * cost is a placeholder until real per-model pricing is configured.
 */
@Component
public class AiUsageRecorder {

    private final JdbcTemplate jdbc;

    public AiUsageRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID ownerId, UUID runId, String stage, TextExtractor.Usage usage) {
        if (usage == null || !usage.called()) {
            return;
        }
        String reservationKey = "run:" + runId + ":stage:" + stage;
        jdbc.update("""
                INSERT INTO usage_ledger
                  (owner_id, run_id, stage, reservation_key, input_tokens, output_tokens, image_units,
                   estimated_usd, status, version)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'SETTLED', 1)
                ON CONFLICT (reservation_key) DO NOTHING
                """,
                ownerId, runId, stage, reservationKey,
                usage.inputTokens(), usage.outputTokens(),
                estimate(usage));
    }

    private BigDecimal estimate(TextExtractor.Usage usage) {
        // placeholder: $0 until real pricing is wired; keeps the ledger shape correct
        return BigDecimal.ZERO;
    }
}
