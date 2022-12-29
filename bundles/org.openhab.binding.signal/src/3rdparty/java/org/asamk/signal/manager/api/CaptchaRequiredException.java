package org.asamk.signal.manager.api;

@SuppressWarnings("serial")
public class CaptchaRequiredException extends Exception {

    public CaptchaRequiredException(final String message) {
        super(message);
    }

    public CaptchaRequiredException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
