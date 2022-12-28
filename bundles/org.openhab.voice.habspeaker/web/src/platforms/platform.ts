import type { MediaSessionCtrl, PlaybackState } from "../stores/media-players/media-session";

export interface Platform {
    getSpeakerId(): Promise<string | null>;
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
    setPlaybackStateListener(listener: (state: PlaybackState, songImage: string, songTile: string) => void): Promise<void>;
}