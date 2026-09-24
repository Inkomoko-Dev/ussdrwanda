package com.inkomoko.mobilebanking.services.integration;

/** Raised when the core lending system cannot be reached or rejects a request. */
public class FineractException extends RuntimeException {
    public FineractException(String message) {
        super(message);
    }

    public FineractException(String message, Throwable cause) {
        super(message, cause);
    }
}
