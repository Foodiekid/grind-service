package com.grindandtrain.common.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import com.grindandtrain.common.error.ErrorCategory;
import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.error.PlatformErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception that leaves a controller into an RFC 9457 problem response, the same way in every service.
 * <ul>
 *   <li>{@link GrindException}: its code, fixed title and the status of its category.</li>
 *   <li>Validation failures: {@code validation_failed}, plus which fields failed and which rule they broke, never the
 *   values that were sent.</li>
 *   <li>Spring's own errors (malformed JSON, wrong method, unknown path): the framework's status with a generic title
 *   and no detail.</li>
 *   <li>Database outages: 503 with {@code Retry-After}, so the app retries instead of giving up.</li>
 *   <li>Anything else is a bug: 500, logged with its stack trace (messages masked by the logging setup).</li>
 * </ul>
 * Expected client errors (4xx) are not logged here; the access log already has the status.
 *
 * @author Dheeraj_Edupuganti
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Seconds the app should wait before retrying after a 503. */
    static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(GrindException.class)
    ResponseEntity<ProblemDetail> handleGrind(GrindException e) {
        ErrorCategory category = e.errorCode().category();
        if (category == ErrorCategory.INTERNAL) {
            // The cause, when there is one, is the real failure; without one the thrower already logged it.
            log.error("Internal error {}", e.errorCode().code(), e.getCause());
        } else if (category == ErrorCategory.UNAVAILABLE) {
            log.warn("Unavailable: {}", e.errorCode().code(), e.getCause());
        }
        return problem(e.errorCode());
    }

    /** Thrown by controllers that read the signed-in user; filter-level failures go to the entry point instead. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException e) {
        return problem(PlatformErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
        return problem(PlatformErrorCode.FORBIDDEN);
    }

    /**
     * A path or query parameter broke a rule of the contract (the generated API interfaces are validated beans, so
     * Spring checks them before the controller runs).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException e) {
        List<Map<String, String>> errors = e.getConstraintViolations().stream()
                .map(violation -> violation(parameterName(violation), constraintName(violation)))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler({TransientDataAccessException.class, DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> handleDatabaseUnavailable(Exception e) {
        log.warn("Database unavailable", e);
        return problem(PlatformErrorCode.UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(PlatformErrorCode.INTERNAL);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> violation(error.getField(), ruleOf(error)))
                .toList();
        return validationProblem(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> violation(result.getMethodParameter().getParameterName(), ruleOf(error))))
                .toList();
        return validationProblem(errors);
    }

    /** Every other framework error: keep its status, replace the body with a generic problem. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Framework error", ex);
            return super.handleExceptionInternal(ex, ProblemDetailFactory.of(PlatformErrorCode.INTERNAL), headers,
                    HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        ProblemDetail problem = status != null
                ? ProblemDetailFactory.of(status, status.name().toLowerCase(), status.getReasonPhrase())
                : ProblemDetail.forStatus(statusCode);
        problem.setInstance(URI.create(ProblemDetailFactory.currentInstance()));
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode error) {
        ProblemDetail problem = ProblemDetailFactory.of(error);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(problem.getStatus());
        if (error.category() == ErrorCategory.UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        }
        return response.body(problem);
    }

    private static ResponseEntity<Object> validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetailFactory.of(PlatformErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static Map<String, String> violation(String field, String rule) {
        return Map.of("field", field == null ? "" : field, "rule", rule);
    }

    /** The last node of the violation's path: the parameter or field name, e.g. {@code provider}. */
    private static String parameterName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }

    private static String constraintName(ConstraintViolation<?> violation) {
        return violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    }

    /** The constraint's short name ({@code NotNull}, {@code Min}, {@code Pattern}), never its message. */
    private static String ruleOf(MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError && fieldError.getCode() != null) {
            return fieldError.getCode();
        }
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? "invalid" : codes[codes.length - 1];
    }
}
