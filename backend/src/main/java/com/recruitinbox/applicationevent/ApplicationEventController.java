package com.recruitinbox.applicationevent;

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

import com.recruitinbox.applicationevent.dto.CreateEventRequest;
import com.recruitinbox.applicationevent.dto.EventResponse;
import com.recruitinbox.applicationevent.dto.UpdateEventRequest;
import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.common.web.VersionHeader;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ApplicationEventController {

    private final ApplicationEventService service;
    private final CurrentUserProvider currentUser;

    public ApplicationEventController(ApplicationEventService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/applications/{applicationId}/events")
    public List<EventResponse> list(@PathVariable UUID applicationId) {
        return service.list(currentUser.requireCurrentUserId(), applicationId);
    }

    @PostMapping("/applications/{applicationId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@PathVariable UUID applicationId, @RequestBody @Valid CreateEventRequest req) {
        return service.create(currentUser.requireCurrentUserId(), applicationId, req);
    }

    @GetMapping("/events/{eventId}")
    public EventResponse get(@PathVariable UUID eventId) {
        return service.get(currentUser.requireCurrentUserId(), eventId);
    }

    @PatchMapping("/events/{eventId}")
    public EventResponse update(@PathVariable UUID eventId, @RequestBody @Valid UpdateEventRequest req) {
        return service.update(currentUser.requireCurrentUserId(), eventId, req);
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID eventId, @RequestHeader("If-Match") String ifMatch) {
        service.delete(currentUser.requireCurrentUserId(), eventId, VersionHeader.parse(ifMatch));
    }
}
