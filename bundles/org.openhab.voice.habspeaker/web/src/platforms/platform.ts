import type { MediaSessionCtrl, PlaybackState } from "../stores/media-players/media-session";

export type PlatformName = 'web' | 'electron' | 'capacitor';

export interface Platform {
    getName(): PlatformName;
    getServerToken(): Promise<string | null>;
    getSpeakerId(): Promise<string | null>;
    getSpotifyCtrl(): Promise<SpotifyPlatformCtrl>
    getUrlOpenHAB(): Promise<string>;
    dimDeviceScreen(value: boolean): Promise<void>;
    keepDeviceAwake(value: boolean): Promise<void>;
    setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void>;
    setup(startMic: () => void): Promise<void>;
    shouldRedirectToLogin(): Promise<boolean>;
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