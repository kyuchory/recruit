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
@Table(name = "application_essay_revisions")
@Getter
@Setter
public class EssayRevision extends BaseEntity {
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;
    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;
    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;
    @Column(name = "revision_no", nullable = false, updatable = false)
    private int revisionNo;
    @Column(name = "label", length = 100)
    private String label;
    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;
    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 32)
    private EssayLimitType limitType;
    @Column(name = "limit_value")
    private Integer limitValue;
    @Column(name = "answer_text", nullable = false, columnDefinition = "text")
    private String answerText;
    @Column(name = "character_count", nullable = false)
    private int characterCount;
    @Column(name = "no_space_count", nullable = false)
    private int noSpaceCount;
    @Column(name = "utf8_byte_count", nullable = false)
    private int utf8ByteCount;
    @Column(name = "korean_2byte_count", nullable = false)
    private int korean2ByteCount;
}
