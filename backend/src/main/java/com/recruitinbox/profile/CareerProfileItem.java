package com.recruitinbox.profile;

import java.time.LocalDate;
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
@Table(name = "career_profile_items")
@Getter
@Setter
public class CareerProfileItem extends BaseEntity {
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 24)
    private ProfileCategory category;
    @Column(name = "label", nullable = false, length = 200)
    private String label;
    @Column(name = "value_text", nullable = false, columnDefinition = "text")
    private String valueText = "";
    @Column(name = "details", nullable = false, columnDefinition = "text")
    private String details = "";
    @Column(name = "field_values", nullable = false, columnDefinition = "text")
    private String fieldValues = "";
    @Column(name = "started_on")
    private LocalDate startedOn;
    @Column(name = "ended_on")
    private LocalDate endedOn;
    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
