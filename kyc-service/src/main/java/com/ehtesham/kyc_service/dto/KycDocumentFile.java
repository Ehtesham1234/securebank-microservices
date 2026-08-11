package com.ehtesham.kyc_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// C7 fix: previously there was no way for anyone — teller or customer —
// to actually retrieve the file behind a KYC submission. Tellers were
// verifying/rejecting based solely on the documentType/documentNumber
// text fields, never looking at the uploaded ID document itself.
@Getter
@Builder
@AllArgsConstructor
public class KycDocumentFile {
    private final byte[] content;
    private final String contentType;
    private final String filename;
}
