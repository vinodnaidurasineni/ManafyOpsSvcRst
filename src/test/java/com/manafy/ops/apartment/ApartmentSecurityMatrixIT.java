package com.manafy.ops.apartment;

import com.fasterxml.jackson.databind.JsonNode;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 2 security matrix (§28) — explicit negative authorization cases through
 * the HTTP stack. Each test asserts a DENY (403) or appropriate failure.
 */
class ApartmentSecurityMatrixIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    private String userWithRole(String prefix, String role, boolean global) {
        String sub = prefix + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub(prefix, sub, "ACTIVE");
        if (role != null) fx.assignRole(u.getId(), role);
        if (global) fx.grantGlobalScope(u.getId());
        return sub;
    }

    private Map<String, Object> createReq(UUID regionId, UUID areaId) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", "SEC-" + UUID.randomUUID()); m.put("name", "Apt");
        m.put("regionId", regionId.toString()); m.put("areaId", areaId.toString());
        return m;
    }

    private UUID createApartmentAs(String sub, UUID regionId, UUID areaId) throws Exception {
        var res = mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(createReq(regionId, areaId))))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        return UUID.fromString(data.get("id").asText());
    }

    // 1. Onboarder cannot create technician (no technician endpoints in Phase 2; assert
    //    the onboarder cannot activate — a segregation boundary within apartment domain).
    @Test
    void onboarderCannotActivate() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartmentAs(adminSub, r.getId(), a.getId());
        String onbSub = userWithRole("onb", "APARTMENT_ONBOARDER", true);
        mockMvc.perform(post("/api/v1/apartments/" + aptId + "/activate").with(asCognito(onbSub)))
                .andExpect(status().isForbidden());
    }

    // 3. HR cannot activate an apartment (HR has no apartment permissions).
    @Test
    void hrCannotActivateApartment() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartmentAs(adminSub, r.getId(), a.getId());
        String hrSub = userWithRole("hr", "HR", true);
        mockMvc.perform(post("/api/v1/apartments/" + aptId + "/activate").with(asCognito(hrSub)))
                .andExpect(status().isForbidden());
        // HR also cannot even view apartments (no APARTMENT_VIEW).
        mockMvc.perform(get("/api/v1/apartments").with(asCognito(hrSub)))
                .andExpect(status().isForbidden());
    }

    // 4/5/6. Field Officer cannot access apartment/building outside assigned area,
    //        and cannot use an override to cross areas (assignment itself is blocked).
    @Test
    void fieldOfficerCannotCrossAreaOrForceOverride() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        Region r = fx.region("R");
        Area mine = fx.area("MINE", r.getId());
        Area other = fx.area("OTHER", r.getId());
        UUID otherApt = createApartmentAs(adminSub, r.getId(), other.getId());

        String foSub = "fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(mine.getId(), fo.getId(), "PRIMARY");
        // Cannot read other-area apartment.
        mockMvc.perform(get("/api/v1/apartments/" + otherApt).with(asCognito(foSub)))
                .andExpect(status().isForbidden());
        // Admin attempting to assign this FO (who covers MINE) to an OTHER-area apartment
        // is blocked by the area-mismatch rule (no cross-area override).
        mockMvc.perform(post("/api/v1/apartments/" + otherApt + "/field-officer").with(asCognito(adminSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fieldOfficerId", fo.getId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FIELD_OFFICER_AREA_MISMATCH"));
    }

    // 7. Support agent cannot access apartment contact PII (masked / no perm).
    @Test
    void supportAgentCannotSeeContactPii() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartmentAs(adminSub, r.getId(), a.getId());
        // Admin adds a contact with PII.
        mockMvc.perform(post("/api/v1/apartments/" + aptId + "/contacts").with(asCognito(adminSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contactType", "MANAGEMENT", "name", "Mgr",
                                "phone", "9876543210", "email", "mgr@x.com"))))
                .andExpect(status().isOk());
        // Support views contacts → phone/email masked (no APARTMENT_CONTACT_VIEW).
        String supSub = userWithRole("sup", "SUPPORT_AGENT", true);
        mockMvc.perform(get("/api/v1/apartments/" + aptId + "/contacts").with(asCognito(supSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].masked").value(true))
                .andExpect(jsonPath("$.data[0].phone").value(org.hamcrest.Matchers.not("9876543210")))
                .andExpect(jsonPath("$.data[0].email").value(org.hamcrest.Matchers.not("mgr@x.com")));
    }

    // 8. Finance cannot mutate apartment onboarding (no apartment perms).
    @Test
    void financeCannotStartOnboarding() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartmentAs(adminSub, r.getId(), a.getId());
        String finSub = userWithRole("fin", "FINANCE", true);
        mockMvc.perform(post("/api/v1/apartments/onboarding/start").with(asCognito(finSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("apartmentId", aptId.toString()))))
                .andExpect(status().isForbidden());
    }

    // 10. Disabled users cannot perform apartment mutations.
    @Test
    void disabledUserCannotMutate() throws Exception {
        String sub = "dis-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("dis", sub, "DISABLED");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(createReq(r.getId(), a.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_DISABLED"));
    }

    // 12. User cannot access another apartment's document by changing the document id.
    @Test
    void idorDocumentByIdDenied() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        Region r = fx.region("R");
        Area mine = fx.area("MINE", r.getId());
        Area victim = fx.area("VICTIM", r.getId());
        UUID victimApt = createApartmentAs(adminSub, r.getId(), victim.getId());
        var dres = mockMvc.perform(post("/api/v1/apartments/" + victimApt + "/documents").with(asCognito(adminSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("docType", "CONTRACT", "fileName", "c.pdf"))))
                .andExpect(status().isOk()).andReturn();
        UUID docId = UUID.fromString(objectMapper.readTree(dres.getResponse().getContentAsString())
                .at("/data/id").asText());

        String foSub = "doc-fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(mine.getId(), fo.getId(), "PRIMARY");
        // FO from another area tries to fetch the document by id → denied.
        mockMvc.perform(get("/api/v1/documents/" + docId).with(asCognito(foSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // 13. User cannot access another apartment's unit by changing ids (parent-auth).
    @Test
    void idorUnitByIdDenied() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        Region r = fx.region("R");
        Area mine = fx.area("MINE", r.getId());
        Area victim = fx.area("VICTIM", r.getId());
        UUID victimApt = createApartmentAs(adminSub, r.getId(), victim.getId());
        UUID buildingId = UUID.fromString(objectMapper.readTree(
                mockMvc.perform(post("/api/v1/apartments/" + victimApt + "/buildings").with(asCognito(adminSub))
                                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("name", "B1"))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/id").asText());
        UUID unitId = UUID.fromString(objectMapper.readTree(
                mockMvc.perform(post("/api/v1/buildings/" + buildingId + "/units").with(asCognito(adminSub))
                                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("unitNumber", "101"))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/id").asText());

        String foSub = "unit-fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(mine.getId(), fo.getId(), "PRIMARY");
        mockMvc.perform(put("/api/v1/units/" + unitId).with(asCognito(foSub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("unitNumber", "999"))))
                .andExpect(status().isForbidden());
    }

    // 9. MANAFY_ADMIN cannot grant SUPER_ADMIN (Phase 1 boundary preserved).
    @Test
    void manafyAdminCannotGrantSuperAdmin() throws Exception {
        String adminSub = userWithRole("adm", "MANAFY_ADMIN", true);
        OpsUser target = fx.createActiveUser("target");
        mockMvc.perform(post("/api/v1/users/" + target.getId() + "/roles").with(asCognito(adminSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleCode", "SUPER_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ROLE_NOT_ASSIGNABLE"));
    }
}
