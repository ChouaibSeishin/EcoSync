package io.ecosync.web;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> invalid(Exception error, HttpServletRequest request) {
        return result(400, "INVALID_REQUEST", "Check the request and try again.", request);
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(HttpServletRequest request) {
        return result(403, "FORBIDDEN", "You do not have access to this resource.", request);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(HttpServletRequest request) {
        return result(404, "NOT_FOUND", "This resource does not exist.", request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception error, HttpServletRequest request) {
        LOG.error("Request {} failed ({})", request.getAttribute("requestId"), error.getClass().getSimpleName());
        return result(500, "INTERNAL_ERROR", "The request could not be completed.", request);
    }
    private ResponseEntity<ApiError> result(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(status, code, message,
                String.valueOf(request.getAttribute("requestId"))));
    }
}
