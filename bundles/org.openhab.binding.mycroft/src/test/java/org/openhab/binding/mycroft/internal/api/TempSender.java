package org.openhab.binding.mycroft.internal.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.mycroft.internal.api.dto.MessageAudioNext;

public class TempSender implements MycroftConnectionListener {

    protected List<MessageType> filtered = Arrays.asList(MessageType.mycroft_reminder_mycroftai__reminder,
            MessageType.mycroft_date_time_mycroftai__timeskillupdate_display,
            MessageType.mycroft_configuration_mycroftai__configurationskillupdate_remote);

    public static void main(String[] args) throws IOException, InterruptedException {

        TempSender thismain = new TempSender();
        MycroftConnection connection = new MycroftConnection(thismain);
        connection.start("loisirs-pi.lan", 8181);

        connection.sendMessage(new MessageAudioNext());

        Thread.sleep(1000);
        connection.close();
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

}
