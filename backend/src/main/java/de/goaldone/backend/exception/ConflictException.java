package de.goaldone.backend.exception;

import lombok.Getter;

/**
 * General exception representing a conflict in the system, typically used when a business rule is violated.
 */
@Getter
public class ConflictException extends RuntimeException {

    /** The specific error code associated with this conflict. */
    private final String errorCode;

    /**
     * Constructs a new ConflictException with the specified error code.
     * @param errorCode the error code describing the conflict
     */
    public ConflictException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

}
