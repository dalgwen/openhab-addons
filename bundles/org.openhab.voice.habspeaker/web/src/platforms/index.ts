/// <reference types="vite/client" />
import type { Platform, SpeakerLocalSettings } from "./platform";
export * from "./platform";
export const startPlatform: () => Promise<Platform> = async () => {
    // #v-ifdef MODE=electron
    return new PlatformAdapter((await import('./electron/electron-platform')).electronPlatform);
    // #v-endif
    // #v-ifdef MODE=capacitor
    return new PlatformAdapter((await import('./capacitor/capacitor-platform')).capacitorPlatform);
    // #v-endif
    return new PlatformAdapter((await import('./web/web-platform')).webPlatform);
};

class PlatformAdapter implements Platform {
    constructor(private readonly platform: Platform) { }
    async setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void> {
        await this.platform.setLocalSettings(localSettings);
    }
    async getName() {
        return this.platform.getName();
    }
    async keepDeviceAwake(value: boolean) {
        this.platform.keepDeviceAwake(value);
    }
    async dimDeviceScreen(value: boolean) {
        this.platform.dimDeviceScreen(value);
    }
    async setup(onReady: () => Promise<void>) {
        console.info(`main: running ${await this.platform.getName()} setup`);
        return this.platform.setup(onReady);
    }
    async getUrlOpenHAB() {
        if (import.meta.env.VITE_DEV_SERVER_URL) {
            return import.meta.env.VITE_DEV_SERVER_URL;
        }
        return this.platform.getUrlOpenHAB();
    }
    async getUrlLogin() {
        if (import.meta.env.VITE_DEV_OH_PROXY) {
            return import.meta.env.VITE_DEV_OH_PROXY;
        }
        return this.platform.getUrlLogin();
    }
    async redirectToRoot() {
        return this.platform.redirectToRoot();
    }
    async isServerTokenNeeded() {
        return this.platform.isServerTokenNeeded();
    }
    async getSpeakerId() {
        return this.platform.getSpeakerId();
    }
    async getServerToken() {
        return this.platform.getServerToken();
    }
}