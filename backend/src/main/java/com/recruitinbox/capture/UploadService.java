package com.recruitinbox.capture;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.link.LinkRepository;
import com.recruitinbox.storage.ObjectStorage;
import com.recruitinbox.storage.StorageProperties;

@Service
public class UploadService {

    private final CaptureAssetRepository assets;
    private final LinkRepository links;
    private final ObjectStorage storage;
    private final ImageValidator imageValidator;
    private final String publicBaseUrl;

    public UploadService(CaptureAssetRepository assets, LinkRepository links, ObjectStorage storage,
            ImageValidator imageValidator, StorageProperties storageProps) {
        this.assets = assets;
        this.links = links;
        this.storage = storage;
        this.imageValidator = imageValidator;
        this.publicBaseUrl = storageProps.publicBaseUrl();
    }

    public record Created(UUID assetId, String uploadUrl, String uploadMethod, Instant expiresAt) {
    }

    public record AssetView(UUID id, CaptureAssetState state, String mime, Long bytes,
            Integer width, Integer height, String readUrl, Instant expiresAt, long version) {
    }

    @Transactional
    public Created create(UUID ownerId, UUID linkId, String mime) {
        links.findByIdAndOwnerId(linkId, ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "link not found"));
        String normalized = mime == null ? "" : mime.toLowerCase().trim();
        if (!normalized.equals("image/png") && !normalized.equals("image/jpeg") && !normalized.equals("image/webp")) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA, "mime must be image/png, image/jpeg or image/webp");
        }

        CaptureAsset asset = new CaptureAsset();
        asset.setOwnerId(ownerId);
        asset.setLinkId(linkId);
        asset.setKind(CaptureAssetKind.IMAGE);
        asset.setMime(normalized);
        asset.setState(CaptureAssetState.PENDING);
        asset.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        // storage key must satisfy the IMAGE branch of capture_assets_check on the first insert
        asset.setStorageKey("assets/" + ownerId + "/" + linkId + "/" + UUID.randomUUID());
        asset = assets.save(asset);

        return new Created(asset.getId(),
                publicBaseUrl + "/api/v1/uploads/" + asset.getId() + "/content", "POST",
                asset.getExpiresAt());
    }

    @Transactional
    public AssetView putContent(UUID ownerId, UUID assetId, byte[] body, String contentType) {
        CaptureAsset asset = requirePending(ownerId, assetId);
        ImageValidator.Dimensions dim = imageValidator.validate(body,
                contentType != null && !contentType.isBlank() ? contentType : asset.getMime());

        storage.put(asset.getStorageKey(), body, dim.mime());
        asset.setMime(dim.mime());
        asset.setBytes(dim.bytes());
        asset.setSha256(sha256Hex(body));
        asset.setState(CaptureAssetState.READY);
        assets.save(asset);
        return view(asset, dim.width(), dim.height());
    }

    @Transactional
    public AssetView complete(UUID ownerId, UUID assetId) {
        CaptureAsset asset = requirePending(ownerId, assetId);
        byte[] body = storage.get(asset.getStorageKey())
                .orElseThrow(() -> new ApiException(ErrorCode.PRECONDITION_FAILED, "no object was uploaded yet"));
        ImageValidator.Dimensions dim = imageValidator.validate(body, asset.getMime());
        asset.setMime(dim.mime());
        asset.setBytes(dim.bytes());
        asset.setSha256(sha256Hex(body));
        asset.setState(CaptureAssetState.READY);
        assets.save(asset);
        return view(asset, dim.width(), dim.height());
    }

    @Transactional(readOnly = true)
    public AssetView get(UUID ownerId, UUID assetId) {
        CaptureAsset asset = assets.findByIdAndOwnerId(assetId, ownerId)
                .orElseThrow(() -> ApiException.notFound("asset"));
        return view(asset, null, null);
    }

    @Transactional
    public void delete(UUID ownerId, UUID assetId, long expectedVersion) {
        CaptureAsset asset = assets.findByIdAndOwnerId(assetId, ownerId)
                .orElseThrow(() -> ApiException.notFound("asset"));
        if (asset.getVersion() != null && asset.getVersion() != expectedVersion) {
            throw ApiException.versionConflict();
        }
        if (asset.getStorageKey() != null) {
            storage.delete(asset.getStorageKey());
        }
        asset.setState(CaptureAssetState.DELETED);
        assets.save(asset);
    }

    private CaptureAsset requirePending(UUID ownerId, UUID assetId) {
        CaptureAsset asset = assets.findByIdAndOwnerId(assetId, ownerId)
                .orElseThrow(() -> ApiException.notFound("asset"));
        if (asset.getState() != CaptureAssetState.PENDING) {
            throw new ApiException(ErrorCode.PRECONDITION_FAILED, "asset is not in PENDING state");
        }
        if (asset.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.PRECONDITION_FAILED, "upload window expired");
        }
        return asset;
    }

    private AssetView view(CaptureAsset a, Integer w, Integer h) {
        String readUrl = a.getState() == CaptureAssetState.READY && a.getStorageKey() != null
                ? storage.presignedGetUrl(a.getStorageKey(), Duration.ofMinutes(5)).toString() : null;
        return new AssetView(a.getId(), a.getState(), a.getMime(), a.getBytes(), w, h,
                readUrl, a.getExpiresAt(), a.getVersion() == null ? 0L : a.getVersion());
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
