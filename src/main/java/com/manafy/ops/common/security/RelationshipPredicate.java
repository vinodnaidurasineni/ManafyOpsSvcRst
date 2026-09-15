package com.manafy.ops.common.security;

/**
 * Relationship predicates (Artifact #2 §1ter, design DD-33). A predicate is an
 * additional authorization gate based on the actor's relationship to the specific
 * resource — the primary IDOR defense.
 *
 * Foundation implements SELF and AREA_RESPONSIBLE. ASSIGNED, RESIDENT, OWNER and
 * RESOURCE_MANAGER are declared here so the framework shape is fixed now; their
 * concrete resolution lands with their resources in later phases.
 */
public enum RelationshipPredicate {
    SELF,
    ASSIGNED,
    RESIDENT,
    OWNER,
    AREA_RESPONSIBLE,
    RESOURCE_MANAGER
}
