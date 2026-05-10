package de.goaldone.backend.exception;

/**
 * Exception thrown when an incorrect password is provided during password update.
 */
public class IncorrectPasswordException extends RuntimeException {

  /**
   * Constructs a new IncorrectPasswordException with a default message.
   */
  public IncorrectPasswordException() {
    super("Das aktuelle Passwort ist nicht korrekt.");
  }

  /**
   * Constructs a new IncorrectPasswordException with a custom message.
   * @param message the detail message
   */
  public IncorrectPasswordException(String message) {
    super(message);
  }
}
