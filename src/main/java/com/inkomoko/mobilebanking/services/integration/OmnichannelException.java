package com.inkomoko.mobilebanking.services.integration;

/** An Omnichannel call failed. {@code httpStatus} is 0 when no response was received. */
public class OmnichannelException extends FineractException {

    private final int httpStatus;
    private final String errorCode;
    /** The response's {@code data}, when a refusal still carried one (e.g. a FAILED transaction with its requestId). */
    private final Object data;

    public OmnichannelException(String message, int httpStatus, String errorCode, Throwable cause) {
        this(message, httpStatus, errorCode, cause, null);
    }

    public OmnichannelException(String message, int httpStatus, String errorCode, Throwable cause, Object data) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.data = data;
    }

    public Object data() {
        return data;
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
