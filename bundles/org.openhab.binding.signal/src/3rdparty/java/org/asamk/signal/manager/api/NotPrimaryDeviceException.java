package org.asamk.signal.manager.api;

@SuppressWarnings("serial")
public class NotPrimaryDeviceException extends Exception {

    public NotPrimaryDeviceException() {
        super("This function is not supported for linked devices.");
    }
}
