package com.recruitinbox.essay;

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
import com.recruitinbox.essay.EssayDtos.CreateQuestionRequest;
import com.recruitinbox.essay.EssayDtos.CreateRevisionRequest;
import com.recruitinbox.essay.EssayDtos.QuestionResponse;
import com.recruitinbox.essay.EssayDtos.ProgressResponse;
import com.recruitinbox.essay.EssayDtos.RestoreRevisionRequest;
import com.recruitinbox.essay.EssayDtos.RevisionResponse;
import com.recruitinbox.essay.EssayDtos.UpdateQuestionRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class EssayController {
    private final EssayService service;
    private final CurrentUserProvider currentUser;

    public EssayController(EssayService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/applications/{applicationId}/essay-questions")
    public List<QuestionResponse> list(@PathVariable UUID applicationId) {
        return service.list(currentUser.requireCurrentUserId(), applicationId);
    }

    @GetMapping("/essay-progress")
    public List<ProgressResponse> progress() {
        return service.progress(currentUser.requireCurrentUserId());
    }

    @PostMapping("/applications/{applicationId}/essay-questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse create(@PathVariable UUID applicationId,
            @RequestBody @Valid CreateQuestionRequest request) {
        return service.create(currentUser.requireCurrentUserId(), applicationId, request);
    }

    @PatchMapping("/essay-questions/{questionId}")
    public QuestionResponse update(@PathVariable UUID questionId,
            @RequestBody @Valid UpdateQuestionRequest request) {
        return service.update(currentUser.requireCurrentUserId(), questionId, request);
    }

    @DeleteMapping("/essay-questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID questionId, @RequestHeader("If-Match") String ifMatch) {
        service.delete(currentUser.requireCurrentUserId(), questionId, VersionHeader.parse(ifMatch));
    }

    @GetMapping("/essay-questions/{questionId}/revisions")
    public List<RevisionResponse> revisions(@PathVariable UUID questionId) {
        return service.revisions(currentUser.requireCurrentUserId(), questionId);
    }

    @PostMapping("/essay-questions/{questionId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public RevisionResponse createRevision(@PathVariable UUID questionId,
            @RequestBody @Valid CreateRevisionRequest request) {
        return service.createRevision(currentUser.requireCurrentUserId(), questionId, request);
    }

    @PostMapping("/essay-questions/{questionId}/revisions/{revisionId}/restore")
    public QuestionResponse restore(@PathVariable UUID questionId, @PathVariable UUID revisionId,
            @RequestBody @Valid RestoreRevisionRequest request) {
        return service.restore(currentUser.requireCurrentUserId(), questionId, revisionId, request.expectedVersion());
    }
}
