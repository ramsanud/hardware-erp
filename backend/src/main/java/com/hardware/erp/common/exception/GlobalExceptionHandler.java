package com.hardware.erp.common.exception;

import com.hardware.erp.common.dto.ErrorResponse;
import com.hardware.erp.common.web.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.sqm.UnknownPathException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Matches Hibernate's "Could not resolve attribute 'foo' of 'Bar'". */
    private static final Pattern UNKNOWN_ATTRIBUTE =
            Pattern.compile("attribute '([^']+)'");

    private String requestId(HttpServletRequest req) {
        return RequestCorrelationFilter.currentRequestId(req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code,
                                                String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(code, message, req.getRequestURI(), requestId(req)));
    }

    /** Covers BusinessException and every subclass, including auth and not-found. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex,
                                                        HttpServletRequest req) {
        log.warn("[{}] {} at {}: {}", requestId(req), ex.getCode(),
                req.getRequestURI(), ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex,
                                                         HttpServletRequest req) {
        log.warn("[{}] Rate limit hit at {}", requestId(req), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(),
                        req.getRequestURI(), requestId(req)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                fieldErrors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ErrorResponse.validation(
                "Please correct the highlighted fields",
                req.getRequestURI(), requestId(req), fieldErrors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "One or more request parameters are invalid", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "BAD_PARAMETER",
                "Invalid value for parameter '" + ex.getName() + "'", req);
    }

    /** Malformed JSON. The parser message can echo payload content, so it is not returned. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or not valid JSON", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This method is not supported for this endpoint", req);
    }

    /**
     * A required multipart part (e.g. Purchase Import's "file") was missing
     * from the request - found live, security-testing the import endpoint
     * with a request that omitted the file: this previously fell through
     * to the generic 500 handler below, for every caller regardless of
     * permission, since argument binding fails before the method body (and
     * @PreAuthorize, which does correctly run first when the request *is*
     * well-formed) ever executes.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required part '" + ex.getRequestPartName() + "' is missing", req);
    }

    /** A file upload exceeded application.yml's servlet.multipart.max-file-size, or the multipart body was otherwise malformed - never a 500 either way. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(HttpServletRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The uploaded file is too large", req);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The uploaded file could not be read", req);
    }

    /**
     * A request to a multipart endpoint with no multipart Content-Type at
     * all (e.g. no body, or a plain POST) - a *different* exception from
     * MissingServletRequestPartException above, thrown earlier in request
     * handling (handler-mapping lookup, not argument binding) so it needs
     * its own handler. Found alongside it, security-testing the same
     * endpoint with a completely empty request.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Expected a file upload for this request", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint not found", req);
    }

    /**
     * Reached when Spring Security itself rejects the request before any
     * controller runs. Always the generic message.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                AuthException.GENERIC_FAILURE, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission for this action", req);
    }

    /**
     * The database rejected the write. The exception text contains the
     * constraint name and often the offending value, so it is logged and
     * never returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                         HttpServletRequest req) {
        log.warn("[{}] Integrity violation at {}", requestId(req), req.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "This record conflicts with existing data. It may already exist or be in use.",
                req);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "STALE_RECORD",
                "Someone else changed this record. Please reload and try again.", req);
    }

    /**
     * BUG-BE-004: an unknown ?sort= field is a bad request, not a server fault.
     *
     * The list endpoints that bind a Pageable directly let Spring append the
     * client's sort property to the JPQL order-by. An attribute the entity does
     * not have then fails when the query is built - Hibernate's
     * UnknownPathException, or Spring Data's PropertyReferenceException on a
     * derived query - and both landed in handleUnexpected() as a 500. Nine list
     * endpoints answered INTERNAL_ERROR for a typo in a query string.
     *
     * Deliberately narrow. PropertyReferenceException only ever means a
     * property name that does not resolve, so it maps whole. Its Hibernate
     * counterpart arrives wrapped in InvalidDataAccessApiUsageException, which
     * also covers genuine mistakes in our own queries - those must keep
     * surfacing as 500s, so the cause is inspected and anything else is
     * rethrown rather than quietly downgraded to a client error.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleUnknownSortProperty(PropertyReferenceException ex,
                                                                   HttpServletRequest req) {
        log.warn("[{}] Unknown sort property at {}: {}", requestId(req), req.getRequestURI(),
                ex.getPropertyName());
        return badSort(ex.getPropertyName(), req);
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQueryUsage(InvalidDataAccessApiUsageException ex,
                                                                 HttpServletRequest req) {
        // Walked, not read one level down: Hibernate nests UnknownPathException
        // at varying depths depending on where in query building it surfaced,
        // and checking only getCause() misses it for most queries.
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownPathException) {
                log.warn("[{}] Unknown sort property at {}: {}", requestId(req),
                        req.getRequestURI(), cause.getMessage());
                return badSort(attributeNameFrom(cause.getMessage()), req);
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        // Not a client's doing: a query this application built is wrong. Handled
        // here rather than rethrown - Spring does not re-resolve an exception
        // thrown out of an @ExceptionHandler, so rethrowing would escape the
        // ErrorResponse contract and hand the caller a container error page.
        return handleUnexpected(ex, req);
    }

    /**
     * Hibernate names the attribute only inside its message ("Could not resolve
     * attribute 'foo' of '...'"), so it is lifted out to keep the client-facing
     * error as useful as the Spring Data one, which carries it as a field.
     * Returns null if the message ever stops carrying it - the caller degrades
     * to "that field" rather than to a broken sentence.
     */
    private static String attributeNameFrom(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = UNKNOWN_ATTRIBUTE.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private ResponseEntity<ErrorResponse> badSort(String property, HttpServletRequest req) {
        String named = property == null || property.isBlank() ? "that field" : "'" + property + "'";
        return build(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                "Cannot sort by " + named + " - no such field on this resource.", req);
    }

    /** Anything here is a bug. Log it in full, tell the client nothing. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("[{}] Unhandled exception at {}", requestId(req), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Please try again.", req);
    }
}
