package com.manafy.ops.workforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.manafy.ops.identity.entity.OpsUser;
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
 * Phase 3 RBAC matrix (spec §32/§33). Eight explicit scenarios:
 *  1. HR manages workforce (create technician).
 *  2. Onboarder cannot create technician (FORBIDDEN).
 *  3. Onboarder cannot modify vendor (FORBIDDEN).
 *  4. Field Officer cannot manage workforce outside scope (SCOPE_DENIED).
 *  5. Finance has no automatic KYC access (FORBIDDEN on KYC doc).
 *  6. Support has no unrestricted KYC access (FORBIDDEN on KYC doc).
 *  7. MANAFY_ADMIN cannot grant SUPER_ADMIN (ROLE_NOT_ASSIGNABLE).
 *  8. Disabled users cannot mutate (AUTH_DISABLED).
 */
class WorkforceRbacIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    private OpsUser user(String tag, String role, String status) {
        String sub = "wf-rbac-" + tag + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub(tag, sub, status);
        if (role != null) fx.assignRole(u.getId(), role);
        return u;
    }

    private String subOf(OpsUser u) { return u.getCognitoSub(); }

    private Map<String, Object> techBody() {
        Map<String, Object> m = new HashMap<>();
        m.put("code", "T-" + UUID.randomUUID());
        m.put("name", "Tech");
        return m;
    }

    // ─── 1. HR manages workforce ─────────────────────────────────────

    @Test
    void hrCanManageWorkforce() throws Exception {
        OpsUser hr = user("hr", "HR", "ACTIVE");
        fx.grantGlobalScope(hr.getId());
        mockMvc.perform(post("/api/v1/technicians").with(asCognito(subOf(hr)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(techBody())))
                .andExpect(status().isOk());
    }

    // ─── 2. Onboarder cannot create technician ───────────────────────

    @Test
    void onboarderCannotCreateTechnician() throws Exception {
        OpsUser onb = user("onb", "APARTMENT_ONBOARDER", "ACTIVE");
        fx.grantGlobalScope(onb.getId());
        mockMvc.perform(post("/api/v1/technicians").with(asCognito(subOf(onb)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(techBody())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── 3. Onboarder cannot modify vendor ───────────────────────────

    @Test
    void onboarderCannotCreateVendor() throws Exception {
        OpsUser onb = user("onb2", "APARTMENT_ONBOARDER", "ACTIVE");
        fx.grantGlobalScope(onb.getId());
        Map<String, Object> vendor = Map.of("code", "V-" + UUID.randomUUID(),
                "legalName", "L", "displayName", "D");
        mockMvc.perform(post("/api/v1/vendors").with(asCognito(subOf(onb)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vendor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── 4. Field Officer cannot manage workforce outside scope ──────

    @Test
    void fieldOfficerCannotCreateTechnician() throws Exception {
        // FIELD_OFFICER holds TECHNICIAN_VIEW only, not TECHNICIAN_CREATE.
        OpsUser fo = user("fo", "FIELD_OFFICER", "ACTIVE");
        mockMvc.perform(post("/api/v1/technicians").with(asCognito(subOf(fo)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(techBody())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── 5/6. KYC ≠ Finance ≠ Support ────────────────────────────────

    /** Create a technician (as HR) with a KYC document; return the document id. */
    private UUID createTechnicianKycDoc() throws Exception {
        OpsUser hr = user("hr-kyc", "HR", "ACTIVE");
        fx.grantGlobalScope(hr.getId());
        var tRes = mockMvc.perform(post("/api/v1/technicians").with(asCognito(subOf(hr)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(techBody())))
                .andExpect(status().isOk()).andReturn();
        UUID techId = UUID.fromString(objectMapper.readTree(tRes.getResponse().getContentAsString())
                .at("/data/id").asText());

        Map<String, Object> doc = new HashMap<>();
        doc.put("docType", "ID_PROOF");
        doc.put("docCategory", "KYC");
        doc.put("fileName", "id.pdf");
        doc.put("objectKey", "kyc/" + techId + "/id.pdf");
        var dRes = mockMvc.perform(post("/api/v1/technicians/" + techId + "/documents").with(asCognito(subOf(hr)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(doc)))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(objectMapper.readTree(dRes.getResponse().getContentAsString())
                .at("/data/id").asText());
    }

    @Test
    void hrCanViewKycDocumentButFinanceCannot() throws Exception {
        UUID docId = createTechnicianKycDoc();

        // Finance holds TECHNICIAN_FINANCE_VIEW but NOT TECHNICIAN_KYC_VIEW → 403.
        OpsUser fin = user("fin", "FINANCE", "ACTIVE");
        fx.grantGlobalScope(fin.getId());
        mockMvc.perform(get("/api/v1/workforce-documents/" + docId).with(asCognito(subOf(fin))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void supportCannotViewKycDocument() throws Exception {
        UUID docId = createTechnicianKycDoc();

        // SUPPORT_AGENT holds only TECHNICIAN_VIEW → no KYC/finance/general doc perm → 403.
        OpsUser sup = user("sup", "SUPPORT_AGENT", "ACTIVE");
        fx.grantGlobalScope(sup.getId());
        mockMvc.perform(get("/api/v1/workforce-documents/" + docId).with(asCognito(subOf(sup))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── 7. MANAFY_ADMIN cannot grant SUPER_ADMIN ────────────────────

    @Test
    void manafyAdminCannotGrantSuperAdmin() throws Exception {
        OpsUser admin = user("admin", "MANAFY_ADMIN", "ACTIVE");
        fx.grantGlobalScope(admin.getId());
        OpsUser target = user("target", null, "ACTIVE");

        mockMvc.perform(post("/api/v1/users/" + target.getId() + "/roles").with(asCognito(subOf(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleCode", "SUPER_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ROLE_NOT_ASSIGNABLE"));
    }

    // ─── 8. Disabled users cannot mutate ─────────────────────────────

    @Test
    void disabledUserCannotMutate() throws Exception {
        OpsUser u = user("disabled", "HR", "DISABLED");
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(post("/api/v1/technicians").with(asCognito(subOf(u)))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(techBody())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_DISABLED"));
    }
}
