package com.forgeflow.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRoleEnum {

    SUPER_ADMIN("SUPER_ADMIN", "super administrator"),
    PROJECT_MANAGER("PROJECT_MANAGER", "project manager"),
    PRODUCT_MANAGER("PRODUCT_MANAGER", "product manager"),
    REQUIREMENT_OWNER("REQUIREMENT_OWNER", "requirement owner"),
    ARCHITECT("ARCHITECT", "architect"),
    FRONTEND_LEAD("FRONTEND_LEAD", "frontend lead"),
    BACKEND_LEAD("BACKEND_LEAD", "backend lead"),
    TEST_LEAD("TEST_LEAD", "test lead"),
    DEVELOPER("DEVELOPER", "developer");

    private final String code;
    private final String desc;
}
