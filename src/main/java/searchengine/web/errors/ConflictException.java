package searchengine.web.errors;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}