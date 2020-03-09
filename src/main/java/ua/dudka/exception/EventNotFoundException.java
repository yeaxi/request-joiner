package ua.dudka.exception;

public class EventNotFoundException extends RuntimeException {

    private static final String MESSAGE = "Event was not found";

    public EventNotFoundException() {
        super(MESSAGE);
    }
}
