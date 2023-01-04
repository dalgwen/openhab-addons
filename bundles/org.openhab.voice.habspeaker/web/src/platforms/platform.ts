import type { MediaSessionCtrl, PlaybackState } from "../stores/media-players/media-session";

export type PlatformName = 'web' | 'electron';

export interface Platform {
    getName(): PlatformName;
    setup(startMic: () => void): Promise<void>;
    getSpeakerId(): Promise<string | null>;
    getServerToken(): Promise<string | null>;
    getUrlOpenHAB(): Promise<string>;
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
    connect(): Promise<boolean>;
    disconnect(): Promise<void>;
    setPlaybackStateListener(listener: SpotifyPlaybackListener): Promise<void>;
}

export type SpotifyPlaybackListener = (state: PlaybackState, uri: string, songImage: string, songTile: string) => void;