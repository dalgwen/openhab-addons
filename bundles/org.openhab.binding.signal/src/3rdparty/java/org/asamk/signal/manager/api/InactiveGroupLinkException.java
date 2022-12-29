package org.asamk.signal.manager.api;

@SuppressWarnings("serial")
public class InactiveGroupLinkException extends Exception {

    public InactiveGroupLinkException(final String message) {
        super(message);
    }

    public InactiveGroupLinkException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
