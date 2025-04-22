package org.openhab.binding.wyoming.internal.protocol.message;

public interface WyomingMessageListener {

    public void onMessage(WyomingRawMessage message);

}
