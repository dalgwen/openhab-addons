import { Platform } from "../platform";
import { WebSpotifyCtrl } from "./web-spotify-ctrl";

class WebPlatform implements Platform {
    spotifyCtrl = new WebSpotifyCtrl();
    async getSpeakerId(): Promise<string | null> {
        const storagePrefix = "habspeaker.ui:";
        const idLocalStorageKey = `${storagePrefix}id`;
        return localStorage.getItem(idLocalStorageKey);
    }
    async getUrlOpenHAB(): Promise<string> {
        let port = '';
        if ((location.protocol === 'https:' && location.port !== '443') || (location.protocol === 'http:' && location.port !== '80')) {
            port = `:${location.port}`
        }
        return `${location.protocol}//${location.hostname}${port}`
    }
    async getSpotifyCtrl() {
        return this.spotifyCtrl;
    }
}

export const webPlatform = new WebPlatform();