package dev.palimpsest.engine.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps exceptions to application/problem+json with the request id (§5.1). */
@RestControllerAdvice
public class ProblemHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException e, HttpServletRequest req) {
        return build(e.status(), e.title(), e.getMessage(), req);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ProblemDetail handleBadRequest(Exception e, HttpServletRequest req) {
        return build(400, "Bad Request", e.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception e, HttpServletRequest req) {
        return build(500, "Internal Server Error", e.getMessage(), req);
    }

    private ProblemDetail build(int status, String title, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.valueOf(status));
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setInstance(java.net.URI.create(req.getRequestURI()));
        pd.setProperty("requestId", ApiSupport.requestId(req));
        return pd;
    }
}
