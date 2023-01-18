import { Platform, PlatformName, SpeakerLocalSettings } from "../platform";
import { WebSpotifyCtrl } from "./web-spotify-ctrl";

const storagePrefix = "habspeaker.ui:";
const idLocalStorageKey = `${storagePrefix}id`;
export class WebPlatform implements Platform {
    spotifyCtrl = new WebSpotifyCtrl();
    getName(): PlatformName {
        return 'web';
    }
    async setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void> {
        localStorage.setItem(idLocalStorageKey, localSettings.speakerId);
    }
    async shouldRedirectToLogin(): Promise<boolean> {
        return true;
    }
    async getServerToken(): Promise<string | null> {
        // nothing to do
        return null;
    }
    async setup(startMic: () => void): Promise<void> {
        // nothing to do
    }
    async getSpeakerId(): Promise<string | null> {
        return localStorage.getItem(idLocalStorageKey);
    }
    async getUrlOpenHAB(): Promise<string> {
        let port = '';
        if (!((location.protocol === 'https:' && location.port === '443') || (location.protocol === 'http:' && location.port === '80'))) {
            port = `:${location.port}`
        }
        return `${location.protocol}//${location.hostname}${port}`
    }
    async getSpotifyCtrl() {
        return this.spotifyCtrl;
    }
}

export const webPlatform = new WebPlatform();