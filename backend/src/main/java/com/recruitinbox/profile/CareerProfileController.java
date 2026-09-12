package com.recruitinbox.profile;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.common.web.VersionHeader;
import com.recruitinbox.profile.ProfileDtos.CreateProfileItemRequest;
import com.recruitinbox.profile.ProfileDtos.ProfileItemResponse;
import com.recruitinbox.profile.ProfileDtos.UpdateProfileItemRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile-items")
public class CareerProfileController {
    private final CareerProfileService service;
    private final CurrentUserProvider currentUser;

    public CareerProfileController(CareerProfileService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<ProfileItemResponse> list(@RequestParam(required = false) ProfileCategory category) {
        return service.list(currentUser.requireCurrentUserId(), category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileItemResponse create(@RequestBody @Valid CreateProfileItemRequest request) {
        return service.create(currentUser.requireCurrentUserId(), request);
    }

    @PatchMapping("/{itemId}")
    public ProfileItemResponse update(@PathVariable UUID itemId,
            @RequestBody @Valid UpdateProfileItemRequest request) {
        return service.update(currentUser.requireCurrentUserId(), itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID itemId, @RequestHeader("If-Match") String ifMatch) {
        service.delete(currentUser.requireCurrentUserId(), itemId, VersionHeader.parse(ifMatch));
    }
}
