package com.example.customaxonserver.exception;

import com.example.customaxonserver.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input parameters")
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .details(errors)
                .build();

        logger.warn("Validation error: {}", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("Validation constraints violated")
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .details(errors)
                .build();

        logger.warn("Constraint violation: {}", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CommandRoutingException.class)
    public ResponseEntity<ErrorResponse> handleCommandRoutingException(
            CommandRoutingException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Command Routing Failed")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.error("Command routing failed", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(QueryRoutingException.class)
    public ResponseEntity<ErrorResponse> handleQueryRoutingException(
            QueryRoutingException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Query Routing Failed")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.error("Query routing failed", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(EventStoreException.class)
    public ResponseEntity<ErrorResponse> handleEventStoreException(
            EventStoreException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Event Store Error")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.error("Event store error", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Concurrency Conflict")
                .message("The resource was modified by another process. Please retry.")
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.warn("Optimistic locking failure", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(
            DataAccessException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Database Error")
                .message("A database error occurred")
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.error("Database error", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponse> handleRedisConnectionFailure(
            RedisConnectionFailureException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Cache Service Unavailable")
                .message("Cache service is temporarily unavailable")
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.error("Redis connection failure", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

//    @ExceptionHandler(KafkaException.class)
//    public ResponseEntity<ErrorResponse> handleKafkaException(
//            KafkaException ex, WebRequest request) {
//
//        ErrorResponse errorResponse = ErrorResponse.builder()
//                .timestamp(LocalDateTime.now())
//                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
//                .error("Messaging Service Unavailable")
//                .message("Message processing is temporarily unavailable")
//                .path(request.getDescription(false).replace("uri=", ""))
//                .correlationId(MDC.get("correlationId"))
//                .build();
//
//        logger.error("Kafka error", ex);
//        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
//    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeoutException(
            TimeoutException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.REQUEST_TIMEOUT.value())
                .error("Request Timeout")
                .message("The request timed out")
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.warn("Request timeout", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.REQUEST_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .path(request.getDescription(false).replace("uri=", ""))
                .correlationId(MDC.get("correlationId"))
                .build();

        logger.error("Unexpected error", ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}