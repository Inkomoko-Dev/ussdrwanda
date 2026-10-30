package com.inkomoko.mobilebanking.services.integration;

/** An Omnichannel call failed. {@code httpStatus} is 0 when no response was received. */
public class OmnichannelException extends FineractException {

    private final int httpStatus;
    private final String errorCode;

    public OmnichannelException(String message, int httpStatus, String errorCode, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean isNotFound() {
        return httpStatus == 404 || (errorCode != null && errorCode.toUpperCase().contains("NOT_FOUND"));
    }
}
