package com.recruitinbox.application;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.application.dto.ApplicationResponse;
import com.recruitinbox.application.dto.CreateApplicationRequest;
import com.recruitinbox.application.dto.UpdateApplicationRequest;
import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.common.web.PageResponse;
import com.recruitinbox.common.web.VersionHeader;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final CurrentUserProvider currentUser;

    public ApplicationController(ApplicationService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<ApplicationResponse> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return service.list(currentUser.requireCurrentUserId(), status, page, size);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return service.get(currentUser.requireCurrentUserId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@RequestBody @Valid CreateApplicationRequest req) {
        return service.create(currentUser.requireCurrentUserId(), req);
    }

    @PatchMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id, @RequestBody @Valid UpdateApplicationRequest req) {
        return service.update(currentUser.requireCurrentUserId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
        service.delete(currentUser.requireCurrentUserId(), id, VersionHeader.parse(ifMatch));
    }
}
