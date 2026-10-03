package com.manafy.ops.adminapi;

import com.manafy.ops.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bank lookup (ManafyOps compatibility layer). Supports the maid profile's
 * IFSC → bank/branch autofill and a static bank list. Returns a derived
 * bank/branch from the IFSC prefix (no external dependency for dev).
 */
@RestController
@RequestMapping("/api/v1/bank")
public class BankController {

    // IFSC bank-code prefix → display name (common Indian banks).
    private static final Map<String, String> BANKS = Map.ofEntries(
            Map.entry("SBIN", "State Bank of India"),
            Map.entry("HDFC", "HDFC Bank"),
            Map.entry("ICIC", "ICICI Bank"),
            Map.entry("UTIB", "Axis Bank"),
            Map.entry("PUNB", "Punjab National Bank"),
            Map.entry("KKBK", "Kotak Mahindra Bank"),
            Map.entry("YESB", "Yes Bank"),
            Map.entry("IDIB", "Indian Bank"),
            Map.entry("CNRB", "Canara Bank"),
            Map.entry("BARB", "Bank of Baroda"),
            Map.entry("IOBA", "Indian Overseas Bank"),
            Map.entry("UBIN", "Union Bank of India"));

    @GetMapping("/ifsc/{ifsc}")
    public ApiResponse<Map<String, Object>> lookupIfsc(@PathVariable String ifsc) {
        String code = ifsc == null || ifsc.length() < 4 ? "" : ifsc.substring(0, 4).toUpperCase();
        String bankName = BANKS.getOrDefault(code, "Bank");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ifsc", ifsc == null ? null : ifsc.toUpperCase());
        m.put("bankName", bankName);
        m.put("branch", "Branch"); // real branch requires an external directory; app allows manual edit
        m.put("bankCode", code);
        return ApiResponse.ok(m);
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, String>>> list() {
        List<Map<String, String>> out = new java.util.ArrayList<>();
        BANKS.forEach((code, name) -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("name", name);
            out.add(m);
        });
        out.sort((a, b) -> a.get("name").compareTo(b.get("name")));
        return ApiResponse.ok(out);
    }
}
