package com.recruitinbox.capture;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import java.io.IOException;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.common.security.CurrentUserProvider;
import com.recruitinbox.common.web.VersionHeader;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService service;
    private final CurrentUserProvider currentUser;

    public UploadController(UploadService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record CreateUploadRequest(@NotNull UUID linkId, @NotNull String mime) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UploadService.Created create(@RequestBody @Valid CreateUploadRequest req) {
        return service.create(currentUser.requireCurrentUserId(), req.linkId(), req.mime());
    }

    /** Multipart image upload (dev/local flow). S3 flow uses the presigned PUT + {@link #complete}. */
    @PostMapping("/{id}/content")
    public UploadService.AssetView uploadContent(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        byte[] body;
        try {
            body = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "could not read the uploaded file");
        }
        return service.putContent(currentUser.requireCurrentUserId(), id, body, file.getContentType());
    }

    @PostMapping("/{id}/complete")
    public UploadService.AssetView complete(@PathVariable UUID id) {
        return service.complete(currentUser.requireCurrentUserId(), id);
    }

    @GetMapping("/{id}")
    public UploadService.AssetView get(@PathVariable UUID id) {
        return service.get(currentUser.requireCurrentUserId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestHeader("If-Match") String ifMatch) {
        service.delete(currentUser.requireCurrentUserId(), id, VersionHeader.parse(ifMatch));
    }
}
