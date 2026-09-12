package com.recruitinbox.essay;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EssayDtos {
    private EssayDtos() {}

    public record CreateQuestionRequest(
            @NotBlank @Size(max = 5000) String questionText,
            EssayLimitType limitType,
            @Min(1) @Max(1_000_000) Integer limitValue) {}

    public record UpdateQuestionRequest(
            @NotNull Long expectedVersion,
            @Size(max = 5000) String questionText,
            EssayLimitType limitType,
            @Min(1) @Max(1_000_000) Integer limitValue,
            @Size(max = 100_000) String answerText,
            EssayStatus status) {}

    public record CreateRevisionRequest(
            @NotNull Long expectedVersion,
            @Size(max = 100) String label) {}

    public record RestoreRevisionRequest(@NotNull Long expectedVersion) {}

    public record ProgressResponse(UUID applicationId, long totalCount, long completedCount) {}

    public record QuestionResponse(
            UUID id,
            UUID applicationId,
            int sortOrder,
            String questionText,
            EssayLimitType limitType,
            Integer limitValue,
            String answerText,
            EssayStatus status,
            int characterCount,
            int characterCountWithoutSpaces,
            int utf8ByteCount,
            int korean2ByteCount,
            long revisionCount,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        static QuestionResponse from(EssayQuestion question, long revisionCount) {
            EssayCounts counts = EssayCounts.of(question.getAnswerText());
            return new QuestionResponse(question.getId(), question.getApplicationId(), question.getSortOrder(),
                    question.getQuestionText(), question.getLimitType(), question.getLimitValue(),
                    question.getAnswerText(), question.getStatus(), counts.characters(),
                    counts.charactersWithoutSpaces(), counts.utf8Bytes(), counts.korean2Bytes(), revisionCount,
                    question.getVersion(), question.getCreatedAt(), question.getUpdatedAt());
        }
    }

    public record RevisionResponse(
            UUID id,
            UUID questionId,
            int revisionNo,
            String label,
            String questionText,
            EssayLimitType limitType,
            Integer limitValue,
            String answerText,
            int characterCount,
            int characterCountWithoutSpaces,
            int utf8ByteCount,
            int korean2ByteCount,
            Instant createdAt) {
        static RevisionResponse from(EssayRevision revision) {
            return new RevisionResponse(revision.getId(), revision.getQuestionId(), revision.getRevisionNo(),
                    revision.getLabel(), revision.getQuestionText(), revision.getLimitType(), revision.getLimitValue(),
                    revision.getAnswerText(), revision.getCharacterCount(), revision.getNoSpaceCount(),
                    revision.getUtf8ByteCount(), revision.getKorean2ByteCount(), revision.getCreatedAt());
        }
    }
}
