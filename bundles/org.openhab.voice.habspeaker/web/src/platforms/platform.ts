import type { MediaSessionCtrl, PlaybackState } from "../stores/media-players/media-session";

export type PlatformName = 'web' | 'electron' | 'capacitor';

export interface Platform {
    getName(): PlatformName;
    setup(startMic: () => void): Promise<void>;
    getSpeakerId(): Promise<string | null>;
    getServerToken(): Promise<string | null>;
    getUrlOpenHAB(): Promise<string>;
    shouldRedirectToLogin(): Promise<boolean>;
    setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void>;
    getSpotifyCtrl(): Promise<SpotifyPlatformCtrl>
}
export interface SpotifyPlatformCtrl {
    initSpotify(): Promise<void>;
    activateSpotify(): Promise<void>;
    getPlayer(): Promise<MediaSessionCtrl | undefined>;
    setToken(token: string): Promise<void>;
    initPlayer(name: string): Promise<void>;
    isEnabled(): Promise<boolean>;
    playOnThisDevice(mediaUri: string): Promise<void>;
    claimPlayback(): Promise<void>;
    connect(): Promise<boolean>;
    disconnect(): Promise<void>;
    setPlaybackStateListener(listener: SpotifyPlaybackListener): Promise<void>;
}
export type SpeakerLocalSettings = {
    speakerId: string,
    ohToken: string,
    ohUrl: string,
}
export type SpotifyPlaybackListener = (state: PlaybackState, songImage: string, songTile: string) => void;