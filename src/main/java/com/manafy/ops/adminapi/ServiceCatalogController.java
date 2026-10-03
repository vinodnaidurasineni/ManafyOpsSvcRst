package com.manafy.ops.adminapi;

import com.manafy.ops.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service catalog (ManafyOps compatibility layer) — GET /api/v1/services.
 *
 * The admin app uses this list to pick a maid's service/skills. These are the
 * workforce service categories used across the ops backend (matches Helper.category).
 */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {

    private static final List<String[]> SERVICES = List.of(
            new String[]{"MAID", "Maid"},
            new String[]{"COOK", "Cook"},
            new String[]{"PLUMBER", "Plumber"},
            new String[]{"ELECTRICIAN", "Electrician"},
            new String[]{"CLEANER", "Cleaner"},
            new String[]{"CARPENTER", "Carpenter"},
            new String[]{"NANNY", "Nanny"},
            new String[]{"DRIVER", "Driver"});

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (String[] s : SERVICES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("serviceCode", s[0]);
            m.put("serviceName", s[1]);
            m.put("id", s[0]);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }
}
