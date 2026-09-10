package com.recruitinbox.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.common.web.VersionHeader;
import com.recruitinbox.notification.dto.RuleDtos.CreateRuleRequest;
import com.recruitinbox.notification.dto.RuleDtos.RuleResponse;
import com.recruitinbox.notification.dto.RuleDtos.UpdateRuleRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class NotificationRuleController {

    private final NotificationRuleService service;
    private final CurrentUserProvider currentUser;

    public NotificationRuleController(NotificationRuleService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/events/{eventId}/notification-rules")
    public List<RuleResponse> list(@PathVariable UUID eventId) {
        return service.list(currentUser.requireCurrentUserId(), eventId);
    }

    @PostMapping("/events/{eventId}/notification-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@PathVariable UUID eventId, @RequestBody @Valid CreateRuleRequest req) {
        return service.create(currentUser.requireCurrentUserId(), eventId, req);
    }

    @PatchMapping("/notification-rules/{id}")
    public RuleResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateRuleRequest req) {
        return service.update(currentUser.requireCurrentUserId(), id, req);
    }

    @DeleteMapping("/notification-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
        service.delete(currentUser.requireCurrentUserId(), id, VersionHeader.parse(ifMatch));
    }
}
