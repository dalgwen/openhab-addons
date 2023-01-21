/// <reference types="vite/client" />
import type { Platform, SpeakerLocalSettings } from "./platform";
export * from "./platform";
const getPlatform: () => Promise<Platform> = async () => {

    // #v-ifdef MODE=electron
    return (await import('./electron/electron-platform')).electronPlatform;
    // #v-endif

    // #v-ifdef MODE=capacitor
    return (await import('./capacitor/capacitor-platform')).capacitorPlatform;
    // #v-endif

    return (await import('./web/web-platform')).webPlatform;
};

class PlatformAdapter {
    async getName() {
        return (await getPlatform()).getName();
    }
    async keepDeviceAwake(value: boolean) {
        (await getPlatform()).keepDeviceAwake(value);
    }
    async dimDeviceScreen(value: boolean) {
        (await getPlatform()).dimDeviceScreen(value);
    }
    async setup(onReady: () => void) {
        const platform = await getPlatform();
        console.info(`main: running ${platform.getName()} setup`);
        return platform.setup(onReady);
    }
    async getSpotifyCtrl() {
        return (await getPlatform()).getSpotifyCtrl();
    }

    async getUrlOpenHAB() {
        if (import.meta.env.VITE_DEV_SERVER_URL) {
            return import.meta.env.VITE_DEV_SERVER_URL;
        }
        return (await getPlatform()).getUrlOpenHAB();
    }
    async shouldRedirectToLogin() {
        return (await getPlatform()).shouldRedirectToLogin();
    }
    async getSpeakerId() {
        return (await getPlatform()).getSpeakerId();
    }
    async setSpeakerSettings(settings: SpeakerLocalSettings) {
        await (await getPlatform()).setLocalSettings(settings);
    }
    async getServerToken() {
        return (await getPlatform()).getServerToken();
    }
}

export const platform = new PlatformAdapter();