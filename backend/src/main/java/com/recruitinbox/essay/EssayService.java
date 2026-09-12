package com.recruitinbox.essay;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.recruitinbox.application.ApplicationRepository;
import com.recruitinbox.common.error.ApiException;
import com.recruitinbox.common.error.ErrorCode;
import com.recruitinbox.essay.EssayDtos.CreateQuestionRequest;
import com.recruitinbox.essay.EssayDtos.CreateRevisionRequest;
import com.recruitinbox.essay.EssayDtos.QuestionResponse;
import com.recruitinbox.essay.EssayDtos.ProgressResponse;
import com.recruitinbox.essay.EssayDtos.RevisionResponse;
import com.recruitinbox.essay.EssayDtos.UpdateQuestionRequest;

@Service
public class EssayService {
    private final EssayQuestionRepository questions;
    private final EssayRevisionRepository revisions;
    private final ApplicationRepository applications;

    public EssayService(EssayQuestionRepository questions, EssayRevisionRepository revisions,
            ApplicationRepository applications) {
        this.questions = questions;
        this.revisions = revisions;
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> list(UUID ownerId, UUID applicationId) {
        requireApplication(ownerId, applicationId);
        return questions.findByApplicationIdAndOwnerIdOrderBySortOrderAscIdAsc(applicationId, ownerId).stream()
                .map(question -> response(question, ownerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProgressResponse> progress(UUID ownerId) {
        return questions.findProgressByOwnerId(ownerId).stream()
                .map(row -> new ProgressResponse(row.getApplicationId(), row.getTotalCount(), row.getCompletedCount()))
                .toList();
    }

    @Transactional
    public QuestionResponse create(UUID ownerId, UUID applicationId, CreateQuestionRequest request) {
        requireApplication(ownerId, applicationId);
        EssayLimitType limitType = request.limitType() == null ? EssayLimitType.NONE : request.limitType();
        validateLimit(limitType, request.limitValue());
        EssayQuestion question = new EssayQuestion();
        question.setOwnerId(ownerId);
        question.setApplicationId(applicationId);
        question.setSortOrder(questions.nextSortOrder(applicationId, ownerId));
        question.setQuestionText(request.questionText().trim());
        question.setLimitType(limitType);
        question.setLimitValue(limitType == EssayLimitType.NONE ? null : request.limitValue());
        return response(questions.saveAndFlush(question), ownerId);
    }

    @Transactional
    public QuestionResponse update(UUID ownerId, UUID questionId, UpdateQuestionRequest request) {
        EssayQuestion question = requireQuestion(ownerId, questionId);
        requireVersion(question.getVersion(), request.expectedVersion());
        if (request.questionText() != null) {
            String value = request.questionText().trim();
            if (value.isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "questionText must not be blank");
            }
            question.setQuestionText(value);
        }
        EssayLimitType limitType = request.limitType() == null ? question.getLimitType() : request.limitType();
        Integer limitValue = request.limitType() == null && request.limitValue() == null
                ? question.getLimitValue() : request.limitValue();
        validateLimit(limitType, limitValue);
        question.setLimitType(limitType);
        question.setLimitValue(limitType == EssayLimitType.NONE ? null : limitValue);
        if (request.answerText() != null) question.setAnswerText(request.answerText());
        if (request.status() != null) question.setStatus(request.status());
        try {
            return response(questions.saveAndFlush(question), ownerId);
        } catch (OptimisticLockingFailureException ex) {
            throw ApiException.versionConflict();
        }
    }

    @Transactional
    public void delete(UUID ownerId, UUID questionId, long expectedVersion) {
        EssayQuestion question = requireQuestion(ownerId, questionId);
        requireVersion(question.getVersion(), expectedVersion);
        questions.delete(question);
    }

    @Transactional(readOnly = true)
    public List<RevisionResponse> revisions(UUID ownerId, UUID questionId) {
        requireQuestion(ownerId, questionId);
        return revisions.findByQuestionIdAndOwnerIdOrderByRevisionNoDesc(questionId, ownerId)
                .stream().map(RevisionResponse::from).toList();
    }

    @Transactional
    public RevisionResponse createRevision(UUID ownerId, UUID questionId, CreateRevisionRequest request) {
        EssayQuestion question = requireQuestion(ownerId, questionId);
        requireVersion(question.getVersion(), request.expectedVersion());
        EssayCounts counts = EssayCounts.of(question.getAnswerText());
        EssayRevision revision = new EssayRevision();
        revision.setOwnerId(ownerId);
        revision.setApplicationId(question.getApplicationId());
        revision.setQuestionId(questionId);
        revision.setRevisionNo(revisions.nextRevisionNo(questionId, ownerId));
        revision.setLabel(blankToNull(request.label()));
        revision.setQuestionText(question.getQuestionText());
        revision.setLimitType(question.getLimitType());
        revision.setLimitValue(question.getLimitValue());
        revision.setAnswerText(question.getAnswerText());
        revision.setCharacterCount(counts.characters());
        revision.setNoSpaceCount(counts.charactersWithoutSpaces());
        revision.setUtf8ByteCount(counts.utf8Bytes());
        revision.setKorean2ByteCount(counts.korean2Bytes());
        return RevisionResponse.from(revisions.saveAndFlush(revision));
    }

    @Transactional
    public QuestionResponse restore(UUID ownerId, UUID questionId, UUID revisionId, long expectedVersion) {
        EssayQuestion question = requireQuestion(ownerId, questionId);
        requireVersion(question.getVersion(), expectedVersion);
        EssayRevision revision = revisions.findByIdAndQuestionIdAndOwnerId(revisionId, questionId, ownerId)
                .orElseThrow(() -> ApiException.notFound("essay revision"));
        question.setAnswerText(revision.getAnswerText());
        question.setStatus(EssayStatus.DRAFT);
        try {
            return response(questions.saveAndFlush(question), ownerId);
        } catch (OptimisticLockingFailureException ex) {
            throw ApiException.versionConflict();
        }
    }

    private QuestionResponse response(EssayQuestion question, UUID ownerId) {
        return QuestionResponse.from(question, revisions.countByQuestionIdAndOwnerId(question.getId(), ownerId));
    }

    private EssayQuestion requireQuestion(UUID ownerId, UUID questionId) {
        return questions.findByIdAndOwnerId(questionId, ownerId)
                .orElseThrow(() -> ApiException.notFound("essay question"));
    }

    private void requireApplication(UUID ownerId, UUID applicationId) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) {
            throw ApiException.notFound("application");
        }
    }

    private static void validateLimit(EssayLimitType type, Integer value) {
        if (type == EssayLimitType.NONE && value != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "limitValue must be empty when limitType is NONE");
        }
        if (type != EssayLimitType.NONE && (value == null || value < 1 || value > 1_000_000)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "limitValue is required for the selected limit type");
        }
    }

    private static void requireVersion(Long actual, Long expected) {
        if (expected == null) {
            throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, "expectedVersion is required");
        }
        if (!Objects.equals(actual == null ? 0L : actual, expected)) throw ApiException.versionConflict();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
