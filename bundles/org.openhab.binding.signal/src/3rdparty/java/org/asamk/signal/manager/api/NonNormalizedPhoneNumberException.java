package org.asamk.signal.manager.api;

@SuppressWarnings("serial")
public class NonNormalizedPhoneNumberException extends Exception {

    public NonNormalizedPhoneNumberException(final String message) {
        super(message);
    }

    public NonNormalizedPhoneNumberException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
