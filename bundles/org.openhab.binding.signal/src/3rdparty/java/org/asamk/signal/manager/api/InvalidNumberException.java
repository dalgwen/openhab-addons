package org.asamk.signal.manager.api;

@SuppressWarnings("serial")
public class InvalidNumberException extends Exception {

    InvalidNumberException(String message, Throwable e) {
        super(message, e);
    }
}
