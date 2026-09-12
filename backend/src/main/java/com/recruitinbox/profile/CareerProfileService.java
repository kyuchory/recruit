package com.recruitinbox.profile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.profile.ProfileDtos.CreateProfileItemRequest;
import com.recruitinbox.profile.ProfileDtos.ProfileItemResponse;
import com.recruitinbox.profile.ProfileDtos.UpdateProfileItemRequest;

@Service
public class CareerProfileService {
    private final CareerProfileRepository items;
    private final ProfileFieldCipher cipher;
    private final ObjectMapper objectMapper;

    public CareerProfileService(CareerProfileRepository items, ProfileFieldCipher cipher, ObjectMapper objectMapper) {
        this.items = items;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProfileItemResponse> list(UUID ownerId, ProfileCategory category) {
        List<CareerProfileItem> found = category == null
                ? items.findByOwnerIdOrderByCategoryAscSortOrderAscIdAsc(ownerId)
                : items.findByOwnerIdAndCategoryOrderBySortOrderAscIdAsc(ownerId, category);
        return found.stream().map(this::response).toList();
    }

    @Transactional
    public ProfileItemResponse create(UUID ownerId, CreateProfileItemRequest request) {
        validateDates(request.startedOn(), request.endedOn());
        Map<String, String> fields = ProfileFieldDefinitions.sanitize(request.category(), request.fields());
        CareerProfileItem item = new CareerProfileItem();
        item.setOwnerId(ownerId);
        item.setCategory(request.category());
        item.setLabel(ProfileFieldDefinitions.displayLabel(request.category(), fields, request.label()));
        item.setFieldValues(cipher.encrypt(writeFields(fields)));
        item.setValueText(cipher.encrypt(request.valueText()));
        item.setDetails(cipher.encrypt(request.details()));
        item.setStartedOn(request.startedOn());
        item.setEndedOn(request.endedOn());
        item.setSensitive(Boolean.TRUE.equals(request.sensitive()));
        item.setSortOrder(items.nextSortOrder(ownerId, request.category()));
        return response(items.saveAndFlush(item));
    }

    @Transactional
    public ProfileItemResponse update(UUID ownerId, UUID itemId, UpdateProfileItemRequest request) {
        CareerProfileItem item = require(ownerId, itemId);
        requireVersion(item.getVersion(), request.expectedVersion());
        ProfileCategory targetCategory = request.category() == null ? item.getCategory() : request.category();
        Map<String, String> fields = request.fields() == null ? readFields(item.getFieldValues())
                : ProfileFieldDefinitions.sanitize(targetCategory, request.fields());
        if (targetCategory != item.getCategory()) {
            item.setCategory(targetCategory);
            item.setSortOrder(items.nextSortOrder(ownerId, targetCategory));
        }
        if (request.fields() != null) item.setFieldValues(cipher.encrypt(writeFields(fields)));
        if (request.label() != null) {
            String label = request.label().trim();
            if (label.isEmpty()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "label must not be blank");
            item.setLabel(label);
        } else if (request.fields() != null) {
            item.setLabel(ProfileFieldDefinitions.displayLabel(targetCategory, fields, null));
        }
        if (request.valueText() != null) item.setValueText(cipher.encrypt(request.valueText()));
        if (request.details() != null) item.setDetails(cipher.encrypt(request.details()));
        if (Boolean.TRUE.equals(request.clearDates())) {
            item.setStartedOn(null);
            item.setEndedOn(null);
        }
        if (request.startedOn() != null) item.setStartedOn(request.startedOn());
        if (request.endedOn() != null) item.setEndedOn(request.endedOn());
        if (request.sensitive() != null) item.setSensitive(request.sensitive());
        validateDates(item.getStartedOn(), item.getEndedOn());
        try {
            return response(items.saveAndFlush(item));
        } catch (OptimisticLockingFailureException ex) {
            throw ApiException.versionConflict();
        }
    }

    @Transactional
    public void delete(UUID ownerId, UUID itemId, long expectedVersion) {
        CareerProfileItem item = require(ownerId, itemId);
        requireVersion(item.getVersion(), expectedVersion);
        items.delete(item);
    }

    private CareerProfileItem require(UUID ownerId, UUID itemId) {
        return items.findByIdAndOwnerId(itemId, ownerId)
                .orElseThrow(() -> ApiException.notFound("career profile item"));
    }

    private ProfileItemResponse response(CareerProfileItem item) {
        return ProfileItemResponse.from(item, readFields(item.getFieldValues()),
                cipher.decrypt(item.getValueText()), cipher.decrypt(item.getDetails()));
    }

    private String writeFields(Map<String, String> fields) {
        if (fields.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception ex) {
            throw new IllegalStateException("profile fields serialization failed", ex);
        }
    }

    private Map<String, String> readFields(String encryptedFields) {
        String json = cipher.decrypt(encryptedFields);
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("profile fields deserialization failed", ex);
        }
    }

    private static void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "endedOn must not be before startedOn");
        }
    }

    private static void requireVersion(Long actual, Long expected) {
        if (expected == null) throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, "expectedVersion is required");
        if (!Objects.equals(actual == null ? 0L : actual, expected)) throw ApiException.versionConflict();
    }
}
