package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.protocol.IdentityKey;

public class SendMessageResult {
    private final RecipientAddress address;
    private final boolean isSuccess;
    private final boolean isNetworkFailure;
    private final boolean isUnregisteredFailure;
    private final boolean isIdentityFailure;
    private final boolean isRateLimitFailure;
    private final ProofRequiredException proofRequiredFailure;

    public SendMessageResult(@JsonProperty("address") RecipientAddress address, @JsonProperty("isSuccess") boolean isSuccess, @JsonProperty("isNetworkFailure") boolean isNetworkFailure,
            @JsonProperty("isUnregisteredFailure") boolean isUnregisteredFailure, @JsonProperty("isIdentityFailure") boolean isIdentityFailure, @JsonProperty("isRateLimitFailure") boolean isRateLimitFailure,
            @JsonProperty("proofRequiredFailure") ProofRequiredException proofRequiredFailure) {
        super();
        this.address = address;
        this.isSuccess = isSuccess;
        this.isNetworkFailure = isNetworkFailure;
        this.isUnregisteredFailure = isUnregisteredFailure;
        this.isIdentityFailure = isIdentityFailure;
        this.isRateLimitFailure = isRateLimitFailure;
        this.proofRequiredFailure = proofRequiredFailure;
    }

    public static SendMessageResult success(RecipientAddress address) {
        return new SendMessageResult(address, true, false, false, false, false, null);
    }

    public static SendMessageResult networkFailure(RecipientAddress address) {
        return new SendMessageResult(address, false, true, false, false, false, null);
    }

    public static SendMessageResult unregisteredFailure(RecipientAddress address) {
        return new SendMessageResult(address, false, false, true, false, false, null);
    }

    public static SendMessageResult identityFailure(RecipientAddress address, IdentityKey identityKey) {
        return new SendMessageResult(address, false, false, false, true, false, null);
    }

    public static SendMessageResult proofRequiredFailure(RecipientAddress address,
            ProofRequiredException proofRequiredException) {
        return new SendMessageResult(address, false, true, false, false, false, proofRequiredException);
    }

    public static SendMessageResult from(
            final org.whispersystems.signalservice.api.messages.SendMessageResult sendMessageResult,
            RecipientResolver recipientResolver, RecipientAddressResolver addressResolver) {
        return new SendMessageResult(
                addressResolver
                        .resolveRecipientAddress(recipientResolver.resolveRecipient(sendMessageResult.getAddress()))
                        .toApiRecipientAddress(),
                sendMessageResult.isSuccess(), sendMessageResult.isNetworkFailure(),
                sendMessageResult.isUnregisteredFailure(), sendMessageResult.getIdentityFailure() != null,
                sendMessageResult.getRateLimitFailure() != null || sendMessageResult.getProofRequiredFailure() != null,
                sendMessageResult.getProofRequiredFailure() == null ? null
                        : new ProofRequiredException(sendMessageResult.getProofRequiredFailure()));
    }

    public RecipientAddress address() {
        return address;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public boolean isNetworkFailure() {
        return isNetworkFailure;
    }

    public boolean isUnregisteredFailure() {
        return isUnregisteredFailure;
    }

    public boolean isIdentityFailure() {
        return isIdentityFailure;
    }

    public boolean isRateLimitFailure() {
        return isRateLimitFailure;
    }

    public ProofRequiredException proofRequiredFailure() {
        return proofRequiredFailure;
    }
}
