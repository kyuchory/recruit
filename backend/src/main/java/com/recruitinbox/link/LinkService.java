package com.recruitinbox.link;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.recruitinbox.application.Application;
import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.application.ReviewStatus;
import com.recruitinbox.common.idempotency.IdempotencyStore;
import com.recruitinbox.link.dto.LinkImportResponse;
import com.recruitinbox.parser.ExtractionQueue;
import com.recruitinbox.parser.ExtractionRun;
import com.recruitinbox.parser.ExtractionRunRepository;
import com.recruitinbox.parser.ExtractionRunStatus;
import com.recruitinbox.parser.ParserVersions;

/**
 * URL import (v1.1 sections 4, 6.6): validate + normalize -> dedupe by
 * {@code (owner, url_hash)} -> in one transaction create link + default
 * application + QUEUED extraction_run -> commit -> 202. No fetch/AI on the
 * request thread. {@code Idempotency-Key} replays a prior response.
 */
@Service
public class LinkService {

    private final LinkRepository links;
    private final ApplicationRepository applications;
    private final ExtractionRunRepository runs;
    private final IdempotencyStore idempotency;
    private final ExtractionQueue queue;

    public LinkService(LinkRepository links, ApplicationRepository applications, ExtractionRunRepository runs,
            IdempotencyStore idempotency, ExtractionQueue queue) {
        this.links = links;
        this.applications = applications;
        this.runs = runs;
        this.idempotency = idempotency;
        this.queue = queue;
    }

    @Transactional
    public ResponseEntity<Object> importUrl(UUID ownerId, String rawUrl, String idempotencyKey) {
        UrlNormalizer.Normalized n = UrlNormalizer.normalize(rawUrl);
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        String requestHash = UrlNormalizer.sha256Hex("POST /api/v1/links\n" + n.normalized());

        if (key != null) {
            var replay = idempotency.reserveOrReplay(ownerId, key, requestHash);
            if (replay.isPresent()) {
                return ResponseEntity.status(replay.get().status()).body(replay.get().body());
            }
        }

        ResponseEntity<Object> response = links.findByOwnerIdAndUrlHash(ownerId, n.urlHash())
                .map(existing -> duplicateResponse(ownerId, existing))
                .orElseGet(() -> createFresh(ownerId, n));

        if (key != null) {
            idempotency.complete(ownerId, key, response.getStatusCode().value(), response.getBody());
        }
        return response;
    }

    private ResponseEntity<Object> duplicateResponse(UUID ownerId, Link link) {
        Application app = applications
                .findByOwnerIdAndLinkIdAndPositionKey(ownerId, link.getId(), "default")
                .orElseGet(() -> createApplication(ownerId, link.getId()));
        return ResponseEntity.status(HttpStatus.OK)
                .body(new LinkImportResponse(link.getId(), app.getId(), null, "DUPLICATE", true));
    }

    private ResponseEntity<Object> createFresh(UUID ownerId, UrlNormalizer.Normalized n) {
        Link link = new Link();
        link.setOwnerId(ownerId);
        link.setSourceChannel("url");
        link.setOriginalUrl(n.original());
        link.setNormalizedUrl(n.normalized());
        link.setUrlHash(n.urlHash());
        link.setExtractionGeneration(1);
        link = links.save(link);

        Application app = createApplication(ownerId, link.getId());

        ExtractionRun run = new ExtractionRun();
        run.setOwnerId(ownerId);
        run.setLinkId(link.getId());
        run.setGeneration(1);
        run.setSourceKind("url");
        run.setStatus(ExtractionRunStatus.QUEUED);
        run.setSchemaVersion(ParserVersions.SCHEMA);
        run.setParserVersion(ParserVersions.PARSER);
        run.setModelConfigVersion(ParserVersions.MODEL_CONFIG);
        run.setNextAttemptAt(Instant.now());
        run = runs.save(run);

        final UUID runId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                queue.signal(runId);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LinkImportResponse(link.getId(), app.getId(), runId, "QUEUED", false));
    }

    private Application createApplication(UUID ownerId, UUID linkId) {
        Application a = new Application();
        a.setOwnerId(ownerId);
        a.setLinkId(linkId);
        a.setPositionKey("default");
        a.setReviewStatus(ReviewStatus.PENDING);
        return applications.save(a);
    }
}
