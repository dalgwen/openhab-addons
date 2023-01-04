import { Platform, PlatformName } from "../platform";
import { ElectronSpotifyCtrl } from "./electron-spotify-ctrl";
class ElectronPlatform implements Platform {
    getName(): PlatformName {
        return 'electron';
    }
    async getServerToken(): Promise<string | null> {
        const ohToken = await window.electronAPI.getTokenOpenHAB();
        return ohToken?.length ? ohToken : null;
    }
    async setup(cb: () => void): Promise<void> {
        window.electronAPI.onReady(cb);
    }
    spotifyCtrl = new ElectronSpotifyCtrl();
    async getSpotifyCtrl() {
        return this.spotifyCtrl;
    }
    getSpeakerId(): Promise<string> {
        return window.electronAPI.getSpeakerId();
    }
    getUrlOpenHAB(): Promise<string> {
        return window.electronAPI.getUrlOpenHAB();
    }
}

export const electronPlatform = new ElectronPlatform();
declare global {
    interface Window {
        electronAPI: {
            onReady(cb: () => void): void;
            getSpeakerId(): Promise<string>;
            getUrlOpenHAB(): Promise<string>;
            getTokenOpenHAB(): Promise<string | undefined>;
            isSpotifyAvailable(): Promise<boolean>;
            startSpotify(label: string): Promise<string>;
            stopSpotify(): Promise<void>;
            getSpotifyId(): Promise<string>;
            setSpotifyToken(spotifyToken: string): Promise<void>;
            setSpotifyPlaybackListener(listener: (state: string) => void): Promise<void>;
        }
    }
}