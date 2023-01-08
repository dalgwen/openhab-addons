import { ref, Ref, watch } from "vue";
import { defineStore } from "pinia";
import { useIOStore } from "../io";
import { useSpotifyPlayerStore } from "./spotify-player";
export const useMediaSessionStore = defineStore("mediaSession", () => {
    const ioStore = useIOStore();
    const spotifyStore = useSpotifyPlayerStore();
    const mediaController: Ref<MediaSessionCtrl | undefined> = ref();
    const mediaState: Ref<PlaybackState> = ref(PlaybackState.STOPPED);
    const mediaProvider = ref("");
    const mediaTarget: Ref<MediaTarget | undefined> = ref();
    let mediaVolume = 100;
    let mediaVolumeBackup = -1;
    let muteMediaCounter = 0;
    let updatingVolume = false;
    function setMediaVolume(value: number) {
        if (mediaVolumeBackup === -1) {
            mediaVolume = value;
            mediaController.value?.setVolume(value);
        } else {
            mediaVolumeBackup = value;
        }
    }
    async function getMediaVolume() {
        if (mediaVolumeBackup === -1) {
            if (mediaController.value && !updatingVolume) {
                mediaVolume = Math.floor(await mediaController.value.getVolume());
            }
            return mediaVolume;
        } else {
            return mediaVolumeBackup;
        };
    }
    function muteMediaVolume(mute: boolean) {
        if (mute) {
            muteMediaCounter += 1;
            if (muteMediaCounter > 0 && mediaVolumeBackup === -1) {
                console.debug('mute media volume');
                mediaVolumeBackup = mediaVolume;
                mediaVolume = 3;
                mediaController.value?.setVolume(mediaVolume)
                    .catch(err => console.error(err));
            }
        } else {
            muteMediaCounter -= 1;
            if (muteMediaCounter < 1 && mediaVolumeBackup !== -1) {
                console.debug('unmute media volume');
                mediaVolume = mediaVolumeBackup;
                mediaVolumeBackup = -1;
                updatingVolume = true;
                mediaController.value?.setVolume(mediaVolume)
                    .then(updateMediaState)
                    .catch(err => console.error(err))
                    .finally(() => updatingVolume = false);
            }
        }
    }
    // media control
    async function updateMediaState() {
        const player = mediaController.value;
        if (player == null) {
            ioStore.sendMediaState({
                totalSeconds: 0,
                currentSecond: 0,
                state: PlaybackState.STOPPED,
                volume: await getMediaVolume(),
                provider: "",
                id: "",
            });
        } else {
            ioStore.sendMediaState({
                totalSeconds: Math.floor(await player.getTotalSeconds()),
                currentSecond: Math.floor(await player.getCurrentSecond()),
                volume: await getMediaVolume(),
                state: await player.getPlaybackState(),
                provider: player.getId(),
                id: await player.getMediaId(),
                playlistId: await player.getPlaylistId(),
                playlistIndex: await player.getPlaylistIndex(),
            });
        }
    }
    let mediaStateUpdateInterval: any = null;
    function startMediaUpdateInterval() {
        // TODO: made interval configurable
        if (mediaStateUpdateInterval == null) {
            mediaStateUpdateInterval = setInterval(updateMediaState, 10000);
        }
    }
    function stopMediaUpdateInterval() {
        if (mediaStateUpdateInterval) {
            clearInterval(mediaStateUpdateInterval);
            mediaStateUpdateInterval = null;
        }
    }
    watch(mediaState, (value) => {
        if (value == PlaybackState.PLAYING) {
            startMediaUpdateInterval();
        } else {
            stopMediaUpdateInterval();
        }
        updateMediaState();
    });
    function updateProvider(provider: string) {
        mediaProvider.value = provider;
    }
    function startMedia(provider: string, media: MediaTarget) {
        stopMediaUpdateInterval();
        console.debug(`starting ${provider} media:`, media);
        switch (provider) {
            case MediaProvider.WEB_AUDIO:
            case MediaProvider.WEB_VIDEO:
            case MediaProvider.YOUTUBE:
                if (!!mediaController.value && !!mediaTarget.value && Object.entries(mediaProvider.value).every(e => media[e[0] as keyof MediaTarget] === e[1])) {
                    // if media is the same we should force a reload
                    const mediaCtrl = mediaController.value;
                    mediaCtrl.seek(media.startSecond ?? 0)
                        .then(() => mediaCtrl.play())
                        .then(() => updateMediaState())
                        .catch((err) => console.error("Error reloading media: ", err));
                } else {
                    // media providers impl should use this data on init and watch its changes.
                    mediaTarget.value = media;
                    updateProvider(provider);
                }
                break;
            case 'spotify':
                // spotify does not use this, so clean it
                mediaTarget.value = undefined;
                if (mediaProvider.value !== MediaProvider.SPOTIFY) {
                    // if current media provider is not spotify unset it, spotify will set itself when the playback starts.
                    mediaProvider.value = "";
                }
                const spotifyUri = media.playlistId ?? media.mediaId;
                if (spotifyUri) {
                    spotifyStore.playUri(spotifyUri)
                        .then(() => startMediaUpdateInterval())
                        .catch((err) => console.error("Error playing spotify media: ", err));
                }
                break;
            default:
                console.error('unsupported media provider ', provider);
                return;
        }
    }
    function stopMedia() {
        stopMediaUpdateInterval();
        mediaController.value = undefined;
        mediaProvider.value = "";
        mediaTarget.value = undefined;
        updateMediaState();
    }
    return {
        mediaController,
        mediaState,
        mediaProvider,
        mediaTarget,
        getMediaVolume,
        setMediaVolume,
        muteMediaVolume,
        startMedia,
        stopMedia,
        updateProvider,
    };
});
export interface MediaTarget {
    mediaId?: string,
    playlistId?: string,
    playlistIndex?: number,
    startSecond?: number
}
export interface MediaSessionCtrl {
    getId(): string;
    getMediaId(): Promise<string>;
    getPlaylistId(): Promise<string | undefined>;
    getPlaylistIndex(): Promise<number | undefined>;
    getAwakeScreen(): boolean;
    getVolume(): Promise<number>;
    setVolume(value: number): Promise<void>;
    play(): Promise<void>;
    pause(): Promise<void>;
    stop(): Promise<void>;
    previous(): Promise<void>;
    next(): Promise<void>;
    seek(second: number): Promise<void>;
    getCurrentSecond(): Promise<number>;
    getTotalSeconds(): Promise<number>;
    getPlaybackState(): Promise<PlaybackState>;
}
export enum MediaProvider {
    YOUTUBE = 'youtube',
    SPOTIFY = 'spotify',
    WEB_AUDIO = 'web-audio',
    WEB_VIDEO = 'web-video',
}
export enum PlaybackState {
    PLAYING = 'playing',
    PAUSED = 'paused',
    STOPPED = 'stopped',
    BUFFERING = 'buffering',
}