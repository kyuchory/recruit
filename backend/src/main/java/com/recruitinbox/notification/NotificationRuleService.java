package com.recruitinbox.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.applicationevent.ApplicationEventRepository;
import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.notification.dto.RuleDtos.CreateRuleRequest;
import com.recruitinbox.notification.dto.RuleDtos.RuleResponse;
import com.recruitinbox.notification.dto.RuleDtos.UpdateRuleRequest;

@Service
public class NotificationRuleService {

    private final NotificationRuleRepository rules;
    private final ApplicationEventRepository events;
    private final NotificationPlanner planner;

    public NotificationRuleService(NotificationRuleRepository rules, ApplicationEventRepository events,
            NotificationPlanner planner) {
        this.rules = rules;
        this.events = events;
        this.planner = planner;
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(UUID ownerId, UUID eventId) {
        requireEvent(ownerId, eventId);
        return rules.findByEventIdAndOwnerId(eventId, ownerId).stream().map(RuleResponse::from).toList();
    }

    @Transactional
    public RuleResponse create(UUID ownerId, UUID eventId, CreateRuleRequest req) {
        requireEvent(ownerId, eventId);
        NotificationRule r = new NotificationRule();
        r.setOwnerId(ownerId);
        r.setEventId(eventId);
        r.setChannel(req.channel());
        r.setAnchor(req.anchor());
        r.setMode(req.mode());
        r.setOffsetMinutes(req.offsetMinutes());
        r.setOffsetDays(req.offsetDays());
        r.setLocalTime(req.localTime());
        r.setEnabled(req.enabled() == null || req.enabled());
        validate(r);
        RuleResponse saved = RuleResponse.from(rules.save(r));
        planner.replan(ownerId, eventId);
        return saved;
    }

    @Transactional
    public RuleResponse update(UUID ownerId, UUID ruleId, UpdateRuleRequest req) {
        NotificationRule r = rules.findByIdAndOwnerId(ruleId, ownerId)
                .orElseThrow(() -> ApiException.notFound("notification rule"));
        if (req.expectedVersion() == null || !req.expectedVersion().equals(r.getVersion())) {
            throw ApiException.versionConflict();
        }
        if (req.channel() != null) {
            r.setChannel(req.channel());
        }
        if (req.anchor() != null) {
            r.setAnchor(req.anchor());
        }
        if (req.mode() != null) {
            r.setMode(req.mode());
        }
        if (req.offsetMinutes() != null) {
            r.setOffsetMinutes(req.offsetMinutes());
        }
        if (req.offsetDays() != null) {
            r.setOffsetDays(req.offsetDays());
        }
        if (req.localTime() != null) {
            r.setLocalTime(req.localTime());
        }
        if (req.enabled() != null) {
            r.setEnabled(req.enabled());
        }
        validate(r);
        RuleResponse saved = RuleResponse.from(rules.saveAndFlush(r));
        planner.replan(ownerId, r.getEventId());
        return saved;
    }

    @Transactional
    public void delete(UUID ownerId, UUID ruleId, long expectedVersion) {
        NotificationRule r = rules.findByIdAndOwnerId(ruleId, ownerId)
                .orElseThrow(() -> ApiException.notFound("notification rule"));
        if (r.getVersion() != null && r.getVersion() != expectedVersion) {
            throw ApiException.versionConflict();
        }
        UUID eventId = r.getEventId();
        rules.delete(r);
        rules.flush();
        planner.replan(ownerId, eventId);
    }

    private void requireEvent(UUID ownerId, UUID eventId) {
        if (!events.existsByIdAndOwnerId(eventId, ownerId)) {
            throw ApiException.notFound("event");
        }
    }

    /** Mirrors the notification_rules CHECK block + MVP channel policy (v1.1 section 6.4, 9.1). */
    private void validate(NotificationRule r) {
        if (r.getChannel() != NotificationChannel.EMAIL && r.getChannel() != NotificationChannel.IN_APP) {
            throw new ApiException(ErrorCode.CHANNEL_NOT_AVAILABLE,
                    "only EMAIL and IN_APP notifications are available in this release");
        }
        if (r.getMode() == NotificationMode.BEFORE_MINUTES) {
            if (r.getAnchor() == NotificationAnchor.DATE) {
                throw bad("BEFORE_MINUTES requires anchor SCHEDULED_AT/START_AT/END_AT");
            }
            if (r.getOffsetMinutes() == null || r.getOffsetMinutes() < 0 || r.getOffsetMinutes() > 43_200) {
                throw bad("offsetMinutes must be between 0 and 43200");
            }
            if (r.getOffsetDays() != null || r.getLocalTime() != null) {
                throw bad("offsetDays/localTime are not allowed for BEFORE_MINUTES");
            }
        } else {
            if (r.getAnchor() != NotificationAnchor.DATE) {
                throw bad("CALENDAR_DAYS requires anchor DATE");
            }
            if (r.getOffsetDays() == null || r.getOffsetDays() < 0 || r.getOffsetDays() > 30) {
                throw bad("offsetDays must be between 0 and 30");
            }
            if (r.getLocalTime() == null) {
                throw bad("localTime is required for CALENDAR_DAYS");
            }
            if (r.getOffsetMinutes() != null) {
                throw bad("offsetMinutes is not allowed for CALENDAR_DAYS");
            }
        }
    }

    private ApiException bad(String msg) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, msg);
    }
}
