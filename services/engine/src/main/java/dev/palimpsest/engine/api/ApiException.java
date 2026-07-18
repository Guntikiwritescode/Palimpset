package dev.palimpsest.engine.api;

/** A controlled API error carrying an HTTP status, title and detail (problem+json). */
public class ApiException extends RuntimeException {
    private final int status;
    private final String title;

    public ApiException(int status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(404, "Not Found", detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(400, "Bad Request", detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(403, "Forbidden", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(409, "Conflict", detail);
    }
}
