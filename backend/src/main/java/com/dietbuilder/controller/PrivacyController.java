package com.dietbuilder.controller;

import com.dietbuilder.model.ConsentRecord;
import com.dietbuilder.service.PrivacyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {
    private final PrivacyService privacyService;

    @PostMapping("/consent")
    public ConsentRecord recordConsent(@RequestBody ConsentRequest request, HttpServletRequest httpRequest) {
        String ipHash = hashIp(httpRequest.getRemoteAddr());
        return privacyService.recordConsent(request.profileId(), request.consents(), ipHash);
    }

    @GetMapping("/consent/{profileId}")
    public ResponseEntity<ConsentRecord> getConsent(@PathVariable String profileId) {
        return privacyService.getConsent(profileId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export/{profileId}")
    public Map<String, Object> exportData(@PathVariable String profileId) {
        return privacyService.exportUserData(profileId);
    }

    @DeleteMapping("/data/{profileId}")
    public ResponseEntity<Void> deleteData(@PathVariable String profileId) {
        return privacyService.deleteUserData(profileId) ? ResponseEntity.ok().build() : ResponseEntity.internalServerError().build();
    }

    record ConsentRequest(String profileId, Map<String, Boolean> consents) {}

    private String hashIp(String ip) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().substring(0, 16);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
