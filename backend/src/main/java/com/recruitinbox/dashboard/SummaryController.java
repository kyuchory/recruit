package com.recruitinbox.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.common.security.CurrentUserProvider;

@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {

    private final SummaryService service;
    private final CurrentUserProvider currentUser;

    public SummaryController(SummaryService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public SummaryResponse summary() {
        return service.forOwner(currentUser.requireCurrentUserId());
    }
}
