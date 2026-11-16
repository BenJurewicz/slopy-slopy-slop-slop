package pl.cloudmigration.api;

public class AppException extends RuntimeException {
    public final int status;
    public final String code;
    public final java.util.List<String> details;
    public AppException(int status, String code, String message) {
        this(status, code, message, java.util.List.of());
    }
    public AppException(int status, String code, String message,
            java.util.List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = java.util.List.copyOf(details);
    }
    public static AppException invalid(String message) {
        return new AppException(422, "VALIDATION", message);
    }
    public static AppException invalid(String field, String message) {
        return new AppException(422, "VALIDATION", message,
                java.util.List.of(field + ": " + message));
    }
    public static AppException conflict(String message) {
        return new AppException(409, "CONFLICT", message);
    }
}
