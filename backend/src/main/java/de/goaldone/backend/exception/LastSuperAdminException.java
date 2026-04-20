package de.goaldone.backend.exception;

public class LastSuperAdminException extends RuntimeException {
    public static final String ERROR_CODE = "LAST_SUPER_ADMIN_CANNOT_BE_DELETED";

    public LastSuperAdminException(String message) {
        super(message);
    }
}
