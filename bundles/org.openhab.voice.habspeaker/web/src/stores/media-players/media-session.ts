import { ref, Ref, watch } from "vue";
import { defineStore } from "pinia";
import { useIOStore } from "../io";
import { useSpotifyPlayerStore } from "./spotify-player";
export const useMediaSessionStore = defineStore("mediaSession", () => {
    const ioStore = useIOStore();
    const spotifyStore = useSpotifyPlayerStore();
    const mediaController: Ref<MediaSessionCtrl | null> = ref(null);
    const mediaState: Ref<PlaybackState> = ref(PlaybackState.STOPPED);
    const mediaProvider = ref("");
    const mediaId = ref("");
    let mediaVolume = 100;
    let mediaVolumeBackup = -1;
    let muteMediaCounter = 0;
    let updatingVolume = false;
    function setMediaVolume(value: number) {
        if (mediaVolumeBackup === -1) {
            mediaVolume = value;
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
            });
        }
    }
    let mediaStateUpdateInterval: any = null;
    watch(mediaState, (value) => {
        if (value == PlaybackState.PLAYING) {
            // TODO: made interval configurable
            if (mediaStateUpdateInterval == null) {
                mediaStateUpdateInterval = setInterval(updateMediaState, 10000);
            }
        } else {
            if (mediaStateUpdateInterval) {
                clearInterval(mediaStateUpdateInterval);
                mediaStateUpdateInterval = null;
            }
        }
        updateMediaState();
    });
    function updateProvider(provider: string, media: string) {
        mediaProvider.value = provider;
        mediaId.value = media;
    }
    function startMedia(provider: string, media: string) {
        if (mediaStateUpdateInterval) {
            clearInterval(mediaStateUpdateInterval);
            mediaStateUpdateInterval = null;
        }
        console.debug(`starting media ${provider}: ${media}`)
        switch (provider) {
            case MediaProvider.WEB_AUDIO:
            case MediaProvider.WEB_VIDEO:
            case MediaProvider.YOUTUBE:
                if (mediaId.value == media && mediaProvider.value == provider && mediaController.value) {
                    // if media is the same we should force a reload
                    const mediaCtrl = mediaController.value;
                    mediaCtrl.seek(0)
                        .then(() => mediaCtrl.play())
                        .catch((err) => console.error("Error reloading media: ", err));
                    // TODO: restart the media state interval is needed?
                } else {
                    updateProvider(provider, media);
                }
                break;
            case 'spotify':
                if (spotifyStore.isConnected()) {
                    spotifyStore.playUri(media);
                }
                break;
            default:
                console.error('unsupported media provider ', provider);
                return;
        }
    }
    function stopMedia() {
        mediaController.value = null;
        mediaProvider.value = "";
        mediaId.value = "";
    }
    return {
        mediaController,
        mediaState,
        mediaProvider,
        mediaId,
        getMediaVolume,
        setMediaVolume,
        muteMediaVolume,
        startMedia,
        stopMedia,
        updateProvider,
    };
});
export interface MediaSessionCtrl {
    getId(): string;
    getMediaId(): Promise<string>;
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