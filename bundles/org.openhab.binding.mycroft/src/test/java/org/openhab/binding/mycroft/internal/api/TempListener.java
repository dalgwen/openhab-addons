package org.openhab.binding.mycroft.internal.api;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.mycroft.internal.api.dto.BaseMessage;

public class TempListener implements MycroftConnectionListener, MycroftMessageListener<@NonNull BaseMessage> {

    protected List<MessageType> filtered = Arrays.asList(MessageType.mycroft_reminder_mycroftai__reminder,
            MessageType.mycroft_date_time_mycroftai__timeskillupdate_display,
            MessageType.mycroft_configuration_mycroftai__configurationskillupdate_remote);

    public static void main(String[] args) {

        TempListener thismain = new TempListener();
        MycroftConnection connection = new MycroftConnection(thismain);
        connection.start("loisirs-pi.lan", 8181);
        connection.registerListener(MessageType.any, thismain);
    }

    @SuppressWarnings("null")
    @Override
    public void connectionEstablished() {
        System.out.println("Etabli");
    }

    @SuppressWarnings("null")
    @Override
    public void connectionLost(@NonNull String reason) {
        System.out.println("Perdu");

    }

    @SuppressWarnings("null")
    @Override
    public void messageReceived(@NonNull BaseMessage message) {
        if (!filtered.contains(message.type)) {
            System.out.println(message.message);
        }
    }

}
