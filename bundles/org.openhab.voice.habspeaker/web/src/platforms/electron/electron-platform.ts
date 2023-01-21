import { Platform, PlatformName, SpeakerLocalSettings } from "../platform";
import { ElectronSpotifyCtrl } from "./electron-spotify-ctrl";
class ElectronPlatform implements Platform {
    spotifyCtrl = new ElectronSpotifyCtrl();
    getName(): PlatformName {
        return 'electron';
    }
    async dimDeviceScreen(value: boolean) {
        console.warn("TODO: implement");
    }
    async keepDeviceAwake(value: boolean) {
        await window.electronAPI.blockSystemSleep(value);
    }
    async shouldRedirectToLogin(): Promise<boolean> {
        return false;
    }
    async getServerToken(): Promise<string | null> {
        const ohToken = await window.electronAPI.getTokenOpenHAB();
        return ohToken?.length ? ohToken : null;
    }
    async setup(cb: () => void): Promise<void> {
        window.electronAPI.onReady(cb);
    }
    async getSpotifyCtrl() {
        return this.spotifyCtrl;
    }
    async setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void> {
        await window.electronAPI.setLocalSettings(localSettings);
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
            blockSystemSleep(value: boolean): Promise<void>;
            getSpeakerId(): Promise<string>;
            getSpotifyId(): Promise<string>;
            getTokenOpenHAB(): Promise<string | undefined>;
            getUrlOpenHAB(): Promise<string>;
            isSpotifyAvailable(): Promise<boolean>;
            onReady(cb: () => void): void;
            startSpotify(label: string): Promise<string>;
            stopSpotify(): Promise<void>;
            setSpotifyToken(spotifyToken: string): Promise<void>;
            setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void>;
            setSpotifyPlaybackListener(listener: (state: string) => void): Promise<void>;
        }
    }
}