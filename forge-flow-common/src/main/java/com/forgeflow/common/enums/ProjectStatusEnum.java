package com.forgeflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProjectStatusEnum {

    DRAFT("DRAFT", "draft"),
    REQUIREMENT_ANALYZING("REQUIREMENT_ANALYZING", "requirement analyzing"),
    REQUIREMENT_REVIEWING("REQUIREMENT_REVIEWING", "requirement reviewing"),
    PRD_GENERATING("PRD_GENERATING", "prd generating"),
    PRD_REVIEWING("PRD_REVIEWING", "prd reviewing"),
    PRD_FROZEN("PRD_FROZEN", "prd frozen"),
    PROTOTYPE_GENERATING("PROTOTYPE_GENERATING", "prototype generating"),
    PROTOTYPE_REVIEWING("PROTOTYPE_REVIEWING", "prototype reviewing"),
    PROTOTYPE_FROZEN("PROTOTYPE_FROZEN", "prototype frozen"),
    ARCHITECTURE_GENERATING("ARCHITECTURE_GENERATING", "architecture generating"),
    ARCHITECTURE_REVIEWING("ARCHITECTURE_REVIEWING", "architecture reviewing"),
    CODE_GENERATING("CODE_GENERATING", "code generating"),
    CODE_VALIDATING("CODE_VALIDATING", "code validating"),
    CODE_REVIEWING("CODE_REVIEWING", "code reviewing"),
    GIT_INITIALIZING("GIT_INITIALIZING", "git initializing"),
    GIT_PUBLISHED("GIT_PUBLISHED", "git published"),
    DEVELOPMENT_STARTED("DEVELOPMENT_STARTED", "development started"),
    REJECTED("REJECTED", "rejected"),
    BLOCKED("BLOCKED", "blocked"),
    FAILED("FAILED", "failed"),
    CANCELLED("CANCELLED", "cancelled");

    private final String code;
    private final String desc;
}
