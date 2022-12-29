package org.asamk.signal.manager.api;

@SuppressWarnings("serial")
public class NotRegisteredException extends Exception {

    public NotRegisteredException() {
        super("User is not registered.");
    }
}
