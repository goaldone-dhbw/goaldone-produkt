package de.goaldone.backend.exception;

/**
 * Exception thrown when trying to reinvite a user who is already active.
 */
public class UserAlreadyActiveException extends ConflictException {
    public UserAlreadyActiveException(String userId) {
        super("USER_ALREADY_ACTIVE: " + userId);
    }
}
