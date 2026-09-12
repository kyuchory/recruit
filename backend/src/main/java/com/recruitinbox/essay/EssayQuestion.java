package com.recruitinbox.essay;

import java.util.UUID;

import com.recruitinbox.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "application_essay_questions")
@Getter
@Setter
public class EssayQuestion extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 32)
    private EssayLimitType limitType = EssayLimitType.NONE;

    @Column(name = "limit_value")
    private Integer limitValue;

    @Column(name = "answer_text", nullable = false, columnDefinition = "text")
    private String answerText = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EssayStatus status = EssayStatus.DRAFT;
}
