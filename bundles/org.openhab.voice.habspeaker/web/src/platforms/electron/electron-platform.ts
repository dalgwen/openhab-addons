import { Platform } from "../platform";
import { ElectronSpotifyCtrl } from "./electron-spotify-ctrl";
class ElectronPlatform implements Platform {
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
            getSpeakerId(): Promise<string>;
            getUrlOpenHAB(): Promise<string>;
            isSpotifyAvailable(): Promise<boolean>;
            startSpotify(label: string): Promise<string>;
            stopSpotify(): Promise<void>;
            getSpotifyId(): Promise<string>;
            setSpotifyPlaybackListener(listener: (state: string) => void): Promise<void>;
        }
    }
}