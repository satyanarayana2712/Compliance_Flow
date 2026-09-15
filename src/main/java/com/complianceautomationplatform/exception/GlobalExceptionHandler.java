package com.abhiram.complianceautomationplatform.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.abhiram.complianceautomationplatform.common.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleResourceNotFound(
                        ResourceNotFoundException ex) {
                return ResponseEntity.status(
                                HttpStatus.NOT_FOUND)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(
                                                                                LocalDateTime.now())
                                                                .status(404)
                                                                .message(
                                                                                ex.getMessage())
                                                                .build());
        }

        @ExceptionHandler(DuplicateResourceException.class)
        public ResponseEntity<ErrorResponse> handleDuplicateResource(
                        DuplicateResourceException ex) {
                return ResponseEntity.status(
                                HttpStatus.CONFLICT)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(
                                                                                LocalDateTime.now())
                                                                .status(409)
                                                                .message(
                                                                                ex.getMessage())
                                                                .build());
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex) {
                // Covers race conditions that slip past application-level
                // existence checks (two concurrent requests both passing
                // an "exists?" check before either commits) and get
                // caught by a database constraint instead, e.g. the
                // unique (compliance_id, assigned_to) constraint on
                // compliance_assignments. The driver-specific message in
                // ex.getMessage() can leak schema details, so we return a
                // generic conflict message instead of ex.getMessage().
                return ResponseEntity.status(
                                HttpStatus.CONFLICT)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(
                                                                                LocalDateTime.now())
                                                                .status(409)
                                                                .message(
                                                                                "This action conflicts with existing data. Please refresh and try again.")
                                                                .build());
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
                return ResponseEntity.status(
                                HttpStatus.BAD_REQUEST)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(
                                                                                LocalDateTime.now())
                                                                .status(400)
                                                                .message(
                                                                                ex.getMessage())
                                                                .build());
        }

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ErrorResponse> handleBusinessException(
                        BusinessException ex) {
                return ResponseEntity.status(
                                HttpStatus.BAD_REQUEST)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(LocalDateTime.now())
                                                                .status(400)
                                                                .message(ex.getMessage())
                                                                .build());
        }

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<ErrorResponse> handleUnauthorized(
                        UnauthorizedException ex) {
                return ResponseEntity.status(
                                HttpStatus.UNAUTHORIZED)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(LocalDateTime.now())
                                                                .status(401)
                                                                .message(ex.getMessage())
                                                                .build());
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(
                        AccessDeniedException ex) {
                return ResponseEntity.status(
                                HttpStatus.FORBIDDEN)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(LocalDateTime.now())
                                                                .status(403)
                                                                .message("Access Denied")
                                                                .build());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, String>> handleValidationException(
                        MethodArgumentNotValidException ex) {
                Map<String, String> errors = new HashMap<>();

                ex.getBindingResult()
                                .getFieldErrors()
                                .forEach(error -> errors.put(
                                                error.getField(),
                                                error.getDefaultMessage()));

                return ResponseEntity.badRequest()
                                .body(errors);
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<String> handleMaxUploadSizeExceeded() {

                return ResponseEntity.badRequest()
                                .body("File size exceeds 10MB limit");
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleException(
                        Exception ex) {

                ex.printStackTrace();

                return ResponseEntity.status(
                                HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                                ErrorResponse.builder()
                                                                .timestamp(
                                                                                LocalDateTime.now())
                                                                .status(500)
                                                                .message(
                                                                                "Internal Server Error")
                                                                .build());
        }
}