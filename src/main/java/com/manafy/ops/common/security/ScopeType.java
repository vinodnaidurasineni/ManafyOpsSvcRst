package com.manafy.ops.common.security;

/**
 * Authorization scope types (Artifact #2 §1, spec §9). Permission and scope are
 * independent gates: holding a permission does not imply access to a specific
 * resource unless the user's scope also covers it.
 */
public enum ScopeType {
    GLOBAL,
    REGION,
    AREA,
    APARTMENT,
    VENDOR,
    SELF,
    ASSIGNED
}
