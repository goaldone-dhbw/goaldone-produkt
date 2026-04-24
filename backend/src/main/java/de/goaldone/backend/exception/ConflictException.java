package de.goaldone.backend.exception;

import lombok.Getter;

@Getter
public class ConflictException extends RuntimeException {

    private final String errorCode;

    public ConflictException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

}
