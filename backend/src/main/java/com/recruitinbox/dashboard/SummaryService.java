package com.recruitinbox.dashboard;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.application.ReviewStatus;
import com.recruitinbox.applicationevent.ApplicationEvent;
import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.applicationevent.EventStatus;
import com.recruitinbox.applicationevent.ScheduleKind;
import com.recruitinbox.user.UserRepository;

/**
 * Builds the {@code /app} summary cards. "오늘" / "이번 주" windows are resolved in
 * the caller's timezone; "이번 주" runs from the start of today to the start of
 * next Monday, so it never counts days already past.
 */
@Service
public class SummaryService {

    /** Enough rows for the card list and for the frontend to filter the table by the same set. */
    private static final int PEEK = 20;

    private final ApplicationEventRepository events;
    private final ApplicationRepository applications;
    private final UserRepository users;

    public SummaryService(ApplicationEventRepository events, ApplicationRepository applications,
            UserRepository users) {
        this.events = events;
        this.applications = applications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public SummaryResponse forOwner(UUID ownerId) {
        ZoneId zone = resolveZone(ownerId);
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate todayDate = now.toLocalDate();
        Instant startOfToday = todayDate.atStartOfDay(zone).toInstant();
        Instant startOfTomorrow = todayDate.plusDays(1).atStartOfDay(zone).toInstant();
        LocalDate nextMonday = todayDate.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant startOfNextWeek = nextMonday.atStartOfDay(zone).toInstant();

        Map<UUID, Application> appsById = applications.findByOwnerId(ownerId).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));

        List<ApplicationEvent> scheduled = events.findByOwnerIdAndStatus(ownerId, EventStatus.SCHEDULED);
        List<ApplicationEvent> todayEvents = new ArrayList<>();
        List<ApplicationEvent> weekEvents = new ArrayList<>();
        for (ApplicationEvent e : scheduled) {
            if (e.getConfirmedAt() == null) {
                continue;
            }
            Instant at = instantOf(e, zone);
            LocalDate onDate = localDateOf(e);
            boolean isToday;
            boolean inWeek;
            if (at != null) {
                isToday = !at.isBefore(startOfToday) && at.isBefore(startOfTomorrow);
                inWeek = !at.isBefore(startOfToday) && at.isBefore(startOfNextWeek);
            } else if (onDate != null) {
                isToday = onDate.isEqual(todayDate);
                inWeek = !onDate.isBefore(todayDate) && onDate.isBefore(nextMonday);
            } else {
                continue;
            }
            if (isToday) {
                todayEvents.add(e);
            }
            if (inWeek) {
                weekEvents.add(e);
            }
        }

        List<Application> reviewApps = applications
                .findByOwnerIdAndReviewStatusAndArchivedAtIsNullOrderByCreatedAtDescIdDesc(
                        ownerId, ReviewStatus.PENDING);

        return new SummaryResponse(
                zone.getId(),
                todayEvents.size(),
                peek(todayEvents, zone, appsById),
                weekEvents.size(),
                peek(weekEvents, zone, appsById),
                reviewApps.size(),
                reviewApps.stream().limit(PEEK)
                        .map(a -> new SummaryResponse.ReviewItem(a.getId(), a.getCompanyName(), a.getPositionTitle()))
                        .toList());
    }

    private List<SummaryResponse.EventItem> peek(List<ApplicationEvent> list, ZoneId zone,
            Map<UUID, Application> appsById) {
        return list.stream()
                .sorted(Comparator.comparing((ApplicationEvent e) -> sortKey(e, zone)))
                .limit(PEEK)
                .map(e -> {
                    Application a = appsById.get(e.getApplicationId());
                    return new SummaryResponse.EventItem(
                            e.getId(),
                            e.getApplicationId(),
                            a == null ? null : a.getCompanyName(),
                            e.getType().name(),
                            e.getCustomLabel(),
                            e.getScheduleKind().name(),
                            e.getScheduledAt() == null ? null : e.getScheduledAt().toString(),
                            e.getScheduledDate() == null ? null : e.getScheduledDate().toString());
                })
                .toList();
    }

    private Instant sortKey(ApplicationEvent e, ZoneId zone) {
        Instant at = instantOf(e, zone);
        if (at != null) {
            return at;
        }
        LocalDate d = localDateOf(e);
        return d == null ? Instant.MAX : d.atStartOfDay(zone).toInstant();
    }

    private Instant instantOf(ApplicationEvent e, ZoneId zone) {
        if (e.getScheduleKind() != ScheduleKind.EXACT) {
            return null;
        }
        return e.getScheduledAt() != null ? e.getScheduledAt() : e.getStartAt();
    }

    private LocalDate localDateOf(ApplicationEvent e) {
        return e.getScheduleKind() == ScheduleKind.DATE_ONLY ? e.getScheduledDate() : null;
    }

    private ZoneId resolveZone(UUID ownerId) {
        String tz = users.findById(ownerId).map(u -> u.getTimezone()).orElse("Asia/Seoul");
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException e) {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
