package org.asamk.signal.manager.api;

@SuppressWarnings("serial")
public class UnregisteredRecipientException extends Exception {

    private final RecipientAddress sender;

    public UnregisteredRecipientException(final RecipientAddress sender) {
        super("Unregistered user: " + sender.getIdentifier());
        this.sender = sender;
    }

    public RecipientAddress getSender() {
        return sender;
    }
}
