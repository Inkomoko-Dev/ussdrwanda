package com.inkomoko.mobilebanking.models.dto;

/** Client as identified in Fineract from the caller's MSISDN. */
public record ClientDto(Long clientId, String displayName, String mobileNo, boolean active) {
}
