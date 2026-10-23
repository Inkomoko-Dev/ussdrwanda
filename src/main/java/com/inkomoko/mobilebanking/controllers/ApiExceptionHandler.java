package com.inkomoko.mobilebanking.controllers;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Maps service-layer validation errors on the back-office API to clean JSON responses. */
@RestControllerAdvice(basePackages = {"com.inkomoko.mobilebanking.controllers.admin",
        "com.inkomoko.mobilebanking.controllers.portal"})
public class ApiExceptionHandler {

    @ExceptionHandler(com.inkomoko.mobilebanking.security.AuthException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized(com.inkomoko.mobilebanking.security.AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> status(ResponseStatusException e) {
        String message = e.getReason() == null ? e.getMessage() : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(ApiResponse.fail(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiResponse.fail(msg));
    }
}
