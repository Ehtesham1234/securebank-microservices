package com.ehtesham.securebank.kyc.controller;

import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.kyc.dto.KycRejectRequest;
import com.ehtesham.securebank.kyc.dto.KycResponse;
import com.ehtesham.securebank.kyc.dto.KycSubmitRequest;
import com.ehtesham.securebank.kyc.service.KycService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "KYC", description = "Know Your Customer verification")
public class KycController {

    private final KycService kycService;
    private final ObjectMapper objectMapper;

    public KycController(
            KycService kycService,
            ObjectMapper objectMapper) {
        this.kycService = kycService;
        this.objectMapper = objectMapper;
    }

    // ── CUSTOMER endpoints ───────────────────────────────────────

    @PostMapping(
            value = "/kyc/submit",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<KycResponse>> submitKyc(
            @RequestPart("data") String dataJson,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails)
            throws JsonProcessingException {

        KycSubmitRequest request = objectMapper.readValue(
                dataJson, KycSubmitRequest.class);

        KycResponse response = kycService.submitKyc(
                request, file, userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("KYC submitted successfully",
                        response));
    }

    @GetMapping("/kyc/status")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<KycResponse>> getMyKycStatus(
            @AuthenticationPrincipal UserDetails userDetails) {

        KycResponse response = kycService
                .getMyKycStatus(userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("KYC status retrieved", response));
    }

    // ── TELLER endpoints ─────────────────────────────────────────

    @GetMapping("/teller/kyc/pending")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<KycResponse>>> getPendingKyc() {

        List<KycResponse> responses = kycService.getPendingKycList();

        return ResponseEntity.ok(
                ApiResponse.success("Pending KYC list retrieved",
                        responses));
    }

    @PostMapping("/teller/kyc/{id}/verify")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<KycResponse>> verifyKyc(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        KycResponse response = kycService.verifyKyc(
                id, userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("KYC verified successfully",
                        response));
    }

    @PostMapping("/teller/kyc/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<KycResponse>> rejectKyc(
            @PathVariable Long id,
            @Valid @RequestBody KycRejectRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        KycResponse response = kycService.rejectKyc(
                id, request, userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("KYC rejected", response));
    }
}