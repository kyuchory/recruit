package com.recruitinbox.profile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ProfileFieldDefinitions {
    private static final Map<ProfileCategory, Definition> DEFINITIONS = definitions();

    private ProfileFieldDefinitions() {}

    static Map<String, String> sanitize(ProfileCategory category, Map<String, String> values) {
        Definition definition = DEFINITIONS.get(category);
        Map<String, String> source = values == null ? Map.of() : values;
        for (String key : source.keySet()) {
            if (!definition.allowedFields().contains(key)) {
                throw new com.recruitinbox.common.error.ApiException(
                        com.recruitinbox.common.error.ErrorCode.VALIDATION_FAILED,
                        "unsupported profile field for " + category + ": " + key);
            }
        }
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        definition.allowedFields().forEach(key -> {
            String value = source.get(key);
            if (value != null && !value.isBlank()) sanitized.put(key, value.trim());
        });
        return sanitized;
    }

    static String displayLabel(ProfileCategory category, Map<String, String> values, String requestedLabel) {
        if (requestedLabel != null && !requestedLabel.isBlank()) return requestedLabel.trim();
        Definition definition = DEFINITIONS.get(category);
        for (String key : definition.labelFields()) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return definition.fallbackLabel();
    }

    private static Map<ProfileCategory, Definition> definitions() {
        Map<ProfileCategory, Definition> result = new LinkedHashMap<>();
        result.put(ProfileCategory.PERSONAL, fixedDef("인적사항",
                "koreanName", "hanjaName", "englishName", "birthDate", "gender", "nationality",
                "postalCode", "roadAddress", "lotAddress", "mobile", "email", "veteransStatus", "disabilityStatus"));
        result.put(ProfileCategory.MILITARY, fixedDef("병역사항",
                "serviceStatus", "branch", "specialty", "rank", "dischargeReason", "startDate", "endDate"));
        result.put(ProfileCategory.EDUCATION, def("학력사항", "schoolName", "major",
                "schoolType", "schoolName", "location", "majorCategory", "major", "attendanceType",
                "admissionDate", "graduationDate", "graduationStatus", "gpa", "gpaScale", "credits",
                "transfer", "doubleMajor", "minor", "gradeSummary", "details"));
        result.put(ProfileCategory.LANGUAGE, def("외국어", "testName", "language",
                "language", "testName", "score", "registrationNumber", "testDate", "acquiredDate",
                "speakingLevel", "issuer"));
        result.put(ProfileCategory.CERTIFICATION, def("자격증", "name", "issuer",
                "name", "grade", "issuer", "acquiredDate", "registrationNumber"));
        result.put(ProfileCategory.CAREER, def("경력사항", "company", "jobTitle",
                "company", "department", "employmentType", "jobTitle", "position", "annualSalary",
                "location", "startDate", "endDate", "leaveReason", "projects", "achievements", "summary"));
        result.put(ProfileCategory.AWARD, def("수상경력", "name", "prize",
                "name", "prize", "issuer", "awardDate", "role", "summary", "details"));
        result.put(ProfileCategory.ACTIVITY, def("활동·교육", "name", "organization",
                "activityType", "name", "organization", "role", "startDate", "endDate", "hours", "summary", "details"));
        result.put(ProfileCategory.SKILL, def("기술", "name", "skillCategory",
                "skillCategory", "name", "level", "duration", "notes"));
        result.put(ProfileCategory.PROJECT, def("프로젝트", "name", "role",
                "name", "summary", "startDate", "endDate", "role", "techStack", "links", "achievements", "details"));
        result.put(ProfileCategory.STORY, def("자소서 소재", "title", "theme",
                "title", "theme", "situation", "task", "action", "result", "lesson", "keywords"));
        return Map.copyOf(result);
    }

    private static Definition def(String fallback, String firstLabel, String secondLabel, String... allowed) {
        return new Definition(Set.of(allowed), java.util.List.of(firstLabel, secondLabel), fallback);
    }

    private static Definition fixedDef(String fallback, String... allowed) {
        return new Definition(Set.of(allowed), java.util.List.of(), fallback);
    }

    private record Definition(Set<String> allowedFields, java.util.List<String> labelFields, String fallbackLabel) {}
}
