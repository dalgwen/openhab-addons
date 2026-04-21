/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.musicassistant.internal.handler;

import java.io.Closeable;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.musicassistant.internal.utils.MusicAssistantTimeoutException;
import org.openhab.core.library.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * Utility class to play a notification message. The message is added
 * to the playlist, played and the previous state of the playlist and the
 * player is restored.
 *
 * @author Mark Hilbush - Initial Contribution
 * @author Patrik Gfeller - Utility class added reduce complexity and length of MusicAssistantPlayerHandler.java
 * @author Mark Hilbush - Convert sound notification volume from channel to config parameter
 *
 */
@NonNullByDefault
class MusicAssistantNotificationPlayer implements Closeable {
    private final Logger logger = LoggerFactory.getLogger(MusicAssistantNotificationPlayer.class);

    // An exception is thrown if we do not receive an acknowledge
    // for a volume set command in the given amount of time [s].
    private static final int VOLUME_COMMAND_TIMEOUT = 4;

    // We expect the media server to acknowledge a playlist command.
    // An exception is thrown if the playlist command was not processed
    // after the defined amount in [s]
    private static final int PLAYLIST_COMMAND_TIMEOUT = 5;

    private final MusicAssistantPlayerState playerState;
    private final MusicAssistantPlayerHandler squeezeBoxPlayerHandler;
    private final MusicAssistantServerHandler squeezeBoxServerHandler;
    private final StringType uri;
    private final String mac;

    boolean playlistModified;

    private int notificationMessagePlaylistsIndex;

    MusicAssistantNotificationPlayer(MusicAssistantPlayerHandler squeezeBoxPlayerHandler,
            MusicAssistantServerHandler squeezeBoxServerHandler, StringType uri) {
        this.squeezeBoxPlayerHandler = squeezeBoxPlayerHandler;
        this.squeezeBoxServerHandler = squeezeBoxServerHandler;
        this.mac = squeezeBoxPlayerHandler.getMac();
        this.uri = uri;
        this.playerState = new MusicAssistantPlayerState(squeezeBoxPlayerHandler);
    }

    void play() throws InterruptedException, MusicAssistantTimeoutException {
        setupPlayerForNotification();
        addNotificationMessageToPlaylist();
        playNotification();
    }

    @Override
    public void close() {
        restorePlayerState();
    }

    private void setupPlayerForNotification() throws InterruptedException, MusicAssistantTimeoutException {
        logger.debug("Setting up player for notification");
        if (playerState.isShuffling()) {
            logger.debug("Turning off shuffle");
            squeezeBoxServerHandler.setShuffleMode(mac, 0);
        }
        if (playerState.isRepeating()) {
            logger.debug("Turning off repeat");
            squeezeBoxServerHandler.setRepeatMode(mac, 0);
        }
        if (playerState.isPlaying()) {
            squeezeBoxServerHandler.stop(mac);
        }
        setVolume(squeezeBoxPlayerHandler.getNotificationSoundVolume().intValue());
    }

    /**
     * Sends a volume set command if target volume is not equal to the current volume.
     *
     * @param requestedVolume The requested volume value.
     * @throws InterruptedException Thread interrupted during while we were waiting for an answer from the media server.
     * @throws MusicAssistantTimeoutException Volume command was not acknowledged by the media server.
     */
    private void setVolume(int requestedVolume) throws InterruptedException, MusicAssistantTimeoutException {
        if (playerState.getVolume() == requestedVolume) {
            return;
        }

        MusicAssistantNotificationListener listener = new MusicAssistantNotificationListener(mac);
        listener.resetVolumeUpdated();

        squeezeBoxServerHandler.registerMusicAssistantPlayerListener(listener);
        squeezeBoxServerHandler.setVolume(mac, requestedVolume);

        logger.trace("Waiting up to {} s for volume to be updated...", VOLUME_COMMAND_TIMEOUT);

        try {
            int timeoutCount = 0;

            while (!listener.isVolumeUpdated(requestedVolume)) {
                Thread.sleep(100);
                if (timeoutCount++ > VOLUME_COMMAND_TIMEOUT * 10) {
                    throw new MusicAssistantTimeoutException("Unable to update volume.");
                }
            }
        } finally {
            squeezeBoxServerHandler.unregisterMusicAssistantPlayerListener(listener);
        }
    }

    private void addNotificationMessageToPlaylist() throws InterruptedException, MusicAssistantTimeoutException {
        logger.debug("Adding notification message to playlist");
        MusicAssistantNotificationListener listener = new MusicAssistantNotificationListener(mac);
        listener.resetPlaylistUpdated();

        squeezeBoxServerHandler.registerMusicAssistantPlayerListener(listener);
        squeezeBoxServerHandler.addPlaylistItem(mac, uri.toString(), "Notification");

        try {
            updatePlaylist(listener);
            this.playlistModified = true;
        } finally {
            squeezeBoxServerHandler.unregisterMusicAssistantPlayerListener(listener);
        }
    }

    private void removeNotificationMessageFromPlaylist() throws InterruptedException, MusicAssistantTimeoutException {
        logger.debug("Removing notification message from playlist");
        MusicAssistantNotificationListener listener = new MusicAssistantNotificationListener(mac);
        listener.resetPlaylistUpdated();

        squeezeBoxServerHandler.registerMusicAssistantPlayerListener(listener);
        squeezeBoxServerHandler.deletePlaylistItem(mac, notificationMessagePlaylistsIndex);

        try {
            updatePlaylist(listener);
        } finally {
            squeezeBoxServerHandler.unregisterMusicAssistantPlayerListener(listener);
        }
    }

    /**
     * Monitor the number of playlist entries. When it changes, then we know the playlist
     * has been updated with the notification URL. There's probably an edge case here where
     * someone is updating the playlist at the same time, but that should be rare.
     *
     * @param listener
     * @throws InterruptedException
     * @throws MusicAssistantTimeoutException
     */
    private void updatePlaylist(MusicAssistantNotificationListener listener)
            throws InterruptedException, MusicAssistantTimeoutException {
        logger.trace("Waiting up to {} s for playlist to be updated...", PLAYLIST_COMMAND_TIMEOUT);

        int timeoutCount = 0;

        while (!listener.isPlaylistUpdated()) {
            Thread.sleep(100);
            if (timeoutCount++ > PLAYLIST_COMMAND_TIMEOUT * 10) {
                logger.debug("Update playlist timed out after {} seconds", PLAYLIST_COMMAND_TIMEOUT);
                throw new MusicAssistantTimeoutException("Unable to update playlist.");
            }
        }
        logger.debug("Playlist updated");
    }

    private void playNotification() throws InterruptedException, MusicAssistantTimeoutException {
        logger.debug("Playing notification");

        notificationMessagePlaylistsIndex = squeezeBoxPlayerHandler.currentNumberPlaylistTracks() - 1;
        MusicAssistantNotificationListener listener = new MusicAssistantNotificationListener(mac);
        listener.resetStopped();

        squeezeBoxServerHandler.registerMusicAssistantPlayerListener(listener);
        squeezeBoxServerHandler.playPlaylistItem(mac, notificationMessagePlaylistsIndex);

        try {
            int notificationTimeout = squeezeBoxPlayerHandler.getNotificationTimeout();
            int timeoutCount = 0;

            logger.trace("Waiting up to {} s for stop...", notificationTimeout);
            while (!listener.isStopped()) {
                Thread.sleep(100);
                if (timeoutCount++ > notificationTimeout * 10) {
                    logger.debug("Notification message timed out after {} seconds", notificationTimeout);
                    throw new MusicAssistantTimeoutException("Notification message timed out");
                }
            }
        } finally {
            squeezeBoxServerHandler.unregisterMusicAssistantPlayerListener(listener);
        }
    }

    private void restorePlayerState() {
        logger.debug("Restoring player state");

        // Mute the player to prevent any noise during the transition to saved state
        // Don't wait for the volume acknowledge as there´s nothing to do about it at this point.
        squeezeBoxServerHandler.setVolume(mac, 0);

        if (playlistModified) {
            try {
                removeNotificationMessageFromPlaylist();
            } catch (InterruptedException | MusicAssistantTimeoutException e) {
                // Not much we can do here except log it and continue on
                logger.debug("Exception while removing notification from playlist: {}", e.getMessage());
            }
        }

        // Resume playing saved playlist item.
        // Note that setting the time doesn't work for remote streams.
        squeezeBoxServerHandler.playPlaylistItem(mac, playerState.getPlaylistIndex());
        squeezeBoxServerHandler.setPlayingTime(mac, playerState.getPlayingTime());

        switch (playerState.getPlayState()) {
            case PLAY:
                logger.debug("Resuming last item playing");
                break;
            case PAUSE:
                /*
                 * If the player was paused, stop it. We stop it because the LMS
                 * doesn't respond to a pause command while it's processing the
                 * above 'playPlaylist item' command. The consequence of this is
                 * we lose the ability to resume local music from saved playing time.
                 */
                logger.debug("Stopping the player");
                squeezeBoxServerHandler.stop(mac);
                break;
            case STOP:
                logger.debug("Stopping the player");
                squeezeBoxServerHandler.stop(mac);
                break;
        }

        // Restore the saved volume level
        squeezeBoxServerHandler.setVolume(mac, playerState.getVolume());

        if (playerState.isShuffling()) {
            logger.debug("Restoring shuffle mode");
            squeezeBoxServerHandler.setShuffleMode(mac, playerState.getShuffle());
        }
        if (playerState.isRepeating()) {
            logger.debug("Restoring repeat mode");
            squeezeBoxServerHandler.setRepeatMode(mac, playerState.getRepeat());
        }
        if (playerState.isMuted()) {
            logger.debug("Re-muting the player");
            squeezeBoxServerHandler.mute(mac);
        }
        if (!playerState.isPoweredOn()) {
            logger.debug("Powering off the player");
            squeezeBoxServerHandler.powerOff(mac);
        }
    }
}
