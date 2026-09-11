package com.recruitinbox.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.user.dto.SettingsResponse;
import com.recruitinbox.user.dto.UpdateSettingsRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService service;
    private final CurrentUserProvider currentUser;

    public SettingsController(SettingsService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public SettingsResponse get() {
        return service.get(currentUser.requireCurrentUserId());
    }

    @PatchMapping
    public SettingsResponse update(@RequestBody @Valid UpdateSettingsRequest req) {
        return service.update(currentUser.requireCurrentUserId(), req);
    }
}
