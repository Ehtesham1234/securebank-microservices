package com.ehtesham.kyc_service.controller;

import com.ehtesham.kyc_service.dto.KycRejectRequest;
import com.ehtesham.kyc_service.dto.KycResponse;
import com.ehtesham.kyc_service.dto.KycSubmitRequest;
import com.ehtesham.kyc_service.dto.response.ApiResponse;
import com.ehtesham.kyc_service.service.KycService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class KycController {

    private final KycService kycService;
    private final ObjectMapper objectMapper;

    public KycController(KycService kycService,
                         ObjectMapper objectMapper) {
        this.kycService = kycService;
        this.objectMapper = objectMapper;
    }

    // ── CUSTOMER endpoints ────────────────────────────────────────

    @PostMapping(
            value = "/kyc/submit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<KycResponse>> submitKyc(
            @RequestPart("data") String dataJson,
            @RequestPart("file") MultipartFile file)
            throws JsonProcessingException {

        KycSubmitRequest request = objectMapper.readValue(
                dataJson, KycSubmitRequest.class);

        return ResponseEntity.ok(ApiResponse.success(
                "KYC submitted successfully",
                kycService.submitKyc(request, file)));
    }

    @GetMapping("/kyc/status")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<KycResponse>>
    getMyKycStatus() {

        return ResponseEntity.ok(ApiResponse.success(
                "KYC status retrieved",
                kycService.getMyKycStatus()));
    }

    // ── TELLER endpoints ──────────────────────────────────────────

    @GetMapping("/teller/kyc/pending")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<KycResponse>>>
    getPendingKyc() {

        return ResponseEntity.ok(ApiResponse.success(
                "Pending KYC list retrieved",
                kycService.getPendingKycList()));
    }

    @PostMapping("/teller/kyc/{id}/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<KycResponse>> verifyKyc(
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.success(
                "KYC verified successfully",
                kycService.verifyKyc(id)));
    }

    @PostMapping("/teller/kyc/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<KycResponse>> rejectKyc(
            @PathVariable Long id,
            @Valid @RequestBody KycRejectRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "KYC rejected",
                kycService.rejectKyc(id, request)));
    }
}