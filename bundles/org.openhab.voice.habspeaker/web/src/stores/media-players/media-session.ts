import { ref, Ref } from "vue";
import { defineStore } from "pinia";
export const useMediaSessionStore = defineStore("mediaSession", () => {
    const mediaController: Ref<MediaSessionCtrl | null> = ref(null);
    const mediaState: Ref<PlaybackState> = ref(PlaybackState.STOPPED);
    return { mediaController, mediaState };
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