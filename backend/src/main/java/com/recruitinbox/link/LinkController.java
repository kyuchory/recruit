package com.recruitinbox.link;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.application.dto.ApplicationResponse;
import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.link.dto.LinkDetailResponse;
import com.recruitinbox.link.dto.LinkImportRequest;
import com.recruitinbox.parser.ExtractionRunRepository;
import com.recruitinbox.parser.dto.ExtractionRunResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class LinkController {

    private final LinkService linkService;
    private final LinkRepository links;
    private final ApplicationRepository applications;
    private final ExtractionRunRepository runs;
    private final CurrentUserProvider currentUser;

    public LinkController(LinkService linkService, LinkRepository links, ApplicationRepository applications,
            ExtractionRunRepository runs, CurrentUserProvider currentUser) {
        this.linkService = linkService;
        this.links = links;
        this.applications = applications;
        this.runs = runs;
        this.currentUser = currentUser;
    }

    @PostMapping("/links")
    public ResponseEntity<Object> importUrl(
            @RequestBody @Valid LinkImportRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return linkService.importUrl(currentUser.requireCurrentUserId(), req.url(), idempotencyKey);
    }

    @GetMapping("/links/{id}")
    public LinkDetailResponse get(@PathVariable UUID id) {
        UUID owner = currentUser.requireCurrentUserId();
        var link = links.findByIdAndOwnerId(id, owner).orElseThrow(() -> ApiException.notFound("link"));
        var apps = applications.findByOwnerIdAndLinkId(owner, id).stream()
                .map(ApplicationResponse::from).toList();
        var latestRun = runs.findFirstByLinkIdAndOwnerIdOrderByGenerationDesc(id, owner)
                .map(ExtractionRunResponse::from).orElse(null);
        return LinkDetailResponse.of(link, apps, latestRun);
    }

    @GetMapping("/extractions/{id}")
    public ExtractionRunResponse getExtraction(@PathVariable UUID id) {
        UUID owner = currentUser.requireCurrentUserId();
        return runs.findByIdAndOwnerId(id, owner)
                .map(ExtractionRunResponse::from)
                .orElseThrow(() -> ApiException.notFound("extraction run"));
    }
}
