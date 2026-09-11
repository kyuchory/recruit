package com.recruitinbox.dashboard;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/summary} -- the three cards on {@code /app}
 * (design v1.1 section 3: "오늘 일정 · 이번 주 일정 · 확인 필요"). All date math is
 * done server-side in the caller's IANA timezone (v1.1 section 8.3).
 */
public record SummaryResponse(
        String timezone,
        long todayCount,
        List<EventItem> today,
        long thisWeekCount,
        List<EventItem> thisWeek,
        long needsReviewCount,
        List<ReviewItem> needsReview) {

    /** A confirmed, scheduled event peeking out of a card. */
    public record EventItem(
            UUID eventId,
            UUID applicationId,
            String companyName,
            String type,
            String label,
            String scheduleKind,
            String scheduledAt,
            String scheduledDate) {
    }

    public record ReviewItem(UUID applicationId, String companyName, String positionTitle) {
    }
}
