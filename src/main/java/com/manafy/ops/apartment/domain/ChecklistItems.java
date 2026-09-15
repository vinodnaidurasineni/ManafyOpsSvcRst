package com.manafy.ops.apartment.domain;

import java.util.List;

/**
 * Canonical onboarding checklist definition (Phase 2 §6, spec §13). Persisted per
 * onboarding record. Mandatory items gate activation. Order is presentation order.
 */
public final class ChecklistItems {

    private ChecklistItems() {}

    public record Def(String key, String label, boolean mandatory) {}

    public static final List<Def> DEFAULTS = List.of(
            new Def("BASIC_INFO",     "Basic apartment information", true),
            new Def("ADDRESS",        "Address",                     true),
            new Def("BUILDINGS",      "Buildings configured",        true),
            new Def("UNITS",          "Units configured",            true),
            new Def("FACILITIES",     "Facilities configured",       false),
            new Def("CONTACTS",       "Management contacts",         true),
            new Def("DOCUMENTS",      "Required documents uploaded", true),
            new Def("SERVICES",       "Service configuration",       false),
            new Def("FIELD_OFFICER",  "Field Officer assigned",      true),
            new Def("VERIFICATION",   "Verification complete",       true)
    );
}
