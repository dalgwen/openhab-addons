import { ref, Ref, watch } from "vue";
import { defineStore } from "pinia";
import { useIOStore } from "../io";
export const useMediaSessionStore = defineStore("mediaSession", () => {
    const ioStore = useIOStore();
    const mediaController: Ref<MediaSessionCtrl | undefined> = ref();
    const mediaState: Ref<PlaybackState> = ref(PlaybackState.STOPPED);
    const mediaProvider = ref("");
    const mediaTarget: Ref<MediaTarget | undefined> = ref();
    let mediaVolume = 100;
    let mediaVolumeBackup = -1;
    let muteMediaCounter = 0;
    let updatingVolume = false;
    watch(mediaController, (mediaController) => {
        if (mediaController) {
            mediaController.getPlaybackState()
                .then((state) => mediaState.value = state);
        }
    })
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
        mediaState.value = PlaybackState.BUFFERING;
    }
    function startMedia(provider: string, media: MediaTarget) {
        stopMediaUpdateInterval();
        console.debug(`starting ${provider} media:`, media);
        switch (provider) {
            case MediaProvider.AUDIO_PLAYER:
            case MediaProvider.VIDEO_PLAYER:
            default:
                console.error('unsupported media provider ', provider);
                return;
        }
    }
    function claimMedia(provider: string) {
        stopMediaUpdateInterval();
        console.debug(`claiming ${provider} media`);
        switch (provider) {
            default:
                console.error('Media claim is not supported by provider ' + provider);
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
        claimMedia,
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
    AUDIO_PLAYER = 'audio-player',
    VIDEO_PLAYER = 'video-player',
}
export enum PlaybackState {
    PLAYING = 'playing',
    PAUSED = 'paused',
    STOPPED = 'stopped',
    BUFFERING = 'buffering',
}