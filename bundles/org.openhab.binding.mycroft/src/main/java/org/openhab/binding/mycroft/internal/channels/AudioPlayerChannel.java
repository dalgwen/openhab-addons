/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mycroft.internal.channels;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mycroft.internal.MycroftBindingConstants;
import org.openhab.binding.mycroft.internal.MycroftHandler;
import org.openhab.binding.mycroft.internal.api.MessageType;
import org.openhab.binding.mycroft.internal.api.dto.BaseMessage;
import org.openhab.binding.mycroft.internal.api.dto.MessageAudioNext;
import org.openhab.binding.mycroft.internal.api.dto.MessageAudioPause;
import org.openhab.binding.mycroft.internal.api.dto.MessageAudioPlay;
import org.openhab.binding.mycroft.internal.api.dto.MessageAudioPrev;
import org.openhab.binding.mycroft.internal.api.dto.MessageAudioResume;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 * This channel handle the Mycroft capability to act as a music player
 * (depending on common play music skills installed)
 * 
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class AudioPlayerChannel extends MycroftChannel<State> {

    public AudioPlayerChannel(MycroftHandler handler) {
        super(handler, MycroftBindingConstants.PLAYER_CHANNEL);
    }

    @Override
    protected @NonNull List<@NonNull MessageType> getMessageToListenTo() {
        return Arrays.asList(MessageType.mycroft_audio_service_prev, MessageType.mycroft_audio_service_next,
                MessageType.mycroft_audio_service_pause, MessageType.mycroft_audio_service_resume,
                MessageType.mycroft_audio_service_play, MessageType.mycroft_audio_service_stop,
                MessageType.mycroft_audio_service_track_info, MessageType.mycroft_audio_service_track_info_reply);
    }

    @Override
    public void messageReceived(@NonNull BaseMessage message) {
        switch (message.type) {
            case mycroft_audio_service_pause:
            case mycroft_audio_service_stop:
                updateMyState(PlayPauseType.PAUSE);
                break;
            case mycroft_audio_service_play:
            case mycroft_audio_service_resume:
                updateMyState(PlayPauseType.PLAY);
                break;
            default:
                break;
        }

    }

    @Override
    public void handleCommand(Command command) {
        if (command instanceof PlayPauseType) {
            if (((PlayPauseType) command) == PlayPauseType.PAUSE) {
                if (handler.sendMessage(new MessageAudioPause())) {
                    updateMyState(PlayPauseType.PAUSE);
                }
            }
            if (((PlayPauseType) command) == PlayPauseType.PLAY) {
                handler.sendMessage(new MessageAudioPlay());
                if (handler.sendMessage(new MessageAudioResume())) {
                    updateMyState(PlayPauseType.PLAY);
                }
            }
        }
        if (command instanceof NextPreviousType) {
            if (((NextPreviousType) command) == NextPreviousType.NEXT) {
                if (handler.sendMessage(new MessageAudioNext())) {
                    updateMyState(PlayPauseType.PLAY);
                }
            }
            if (((NextPreviousType) command) == NextPreviousType.PREVIOUS) {
                if (handler.sendMessage(new MessageAudioPrev())) {
                    updateMyState(PlayPauseType.PLAY);
                }
            }
        }
    }

}
