package searchengine.web.errors;

import java.time.Instant;

public record ApiError(int status, String message, String path, Instant timestamp) {
    public static ApiError of(int status, String message, String path) {
        return new ApiError(status, message, path, Instant.now());
    }
}