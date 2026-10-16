package com.skillhub.exception;

import com.skillhub.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.persistence.OptimisticLockException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestGlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if ("UNAUTHORIZED".equals(ex.getMessage()) || "UNAUTHORIZED_ATTEMPT_ACCESS".equals(ex.getMessage())) {
            status = HttpStatus.FORBIDDEN;
        } else if ("ATTEMPT_LOCKED".equals(ex.getMessage()) || "ATTEMPT_EXPIRED".equals(ex.getMessage())) {
            status = HttpStatus.CONFLICT;
        } else if (ex.getMessage() != null && ex.getMessage().contains("NOT_FOUND")) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage(), formatUserFriendlyMessage(ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("Validation failed: {}", errors);
        ApiResponse<Map<String, String>> response = new ApiResponse<>(false, "VALIDATION_FAILED", "Request validation failed", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("ACCESS_DENIED", "You are not authorized to perform this operation."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("AUTHENTICATION_REQUIRED", "Authentication required to access this resource."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONCURRENT_MODIFICATION", "A conflicting change occurred. Please reload and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Database constraint violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DATA_CONFLICT", "A duplicate or constraint-violating record was encountered."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("FILE_TOO_LARGE", "Uploaded file exceeds maximum permitted file size."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        // Secure logging: do not leak internal exception details, stack traces, or SQL schema to the client
        log.error("Unhandled internal server error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "An unexpected error occurred while processing your request."));
    }

    private String formatUserFriendlyMessage(String code) {
        if (code == null) return "An error occurred.";
        return switch (code) {
            case "UNAUTHORIZED_ATTEMPT_ACCESS" -> "You do not have permission to access this examination attempt.";
            case "APPLICATION_NOT_FOUND" -> "The specified examination application was not found.";
            case "APPLICATION_NOT_APPROVED" -> "This exam application has not yet been approved by an administrator.";
            case "PAYMENT_NOT_COMPLETED" -> "Examination application fee payment has not been completed.";
            case "MAX_ATTEMPTS_EXCEEDED" -> "You have already reached the maximum allowed attempts for this examination.";
            case "ATTEMPT_NOT_FOUND" -> "Examination attempt was not found.";
            case "ATTEMPT_LOCKED" -> "This examination attempt is submitted and locked against further changes.";
            case "ATTEMPT_EXPIRED" -> "The allotted time duration for this examination has expired.";
            case "QUESTION_NOT_FOUND" -> "The requested examination question could not be found.";
            case "RATE_LIMIT_EXCEEDED" -> "Too many requests. Please wait a moment before trying again.";
            case "SUBMISSION_COOLDOWN_ACTIVE" -> "Please wait a few seconds before submitting code again.";
            case "MAX_SUBMISSIONS_REACHED" -> "You have reached the maximum allowed coding submissions for this question.";
            case "UNAUTHORIZED" -> "Authentication required.";
            default -> code.replace('_', ' ').toLowerCase();
        };
    }
}
