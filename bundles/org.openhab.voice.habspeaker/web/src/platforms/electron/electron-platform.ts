import { Platform, PlatformName, SpeakerLocalSettings } from "../platform";
class ElectronPlatform implements Platform {
    getName(): PlatformName {
        return 'electron';
    }
    async dimDeviceScreen(value: boolean) {
        console.warn("TODO: implement");
    }
    async keepDeviceAwake(value: boolean) {
        await window.electronAPI.blockSystemSleep(value);
    }
    async isServerTokenNeeded(): Promise<boolean> {
        return true;
    }
    async getServerToken(): Promise<string | null> {
        const ohToken = await window.electronAPI.getTokenOpenHAB();
        return ohToken?.length ? ohToken : null;
    }
    async setup(startMic: () => Promise<void>): Promise<string | null> {
        window.electronAPI.onReady(startMic);
        return null;
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
            getTokenOpenHAB(): Promise<string | undefined>;
            getUrlOpenHAB(): Promise<string>;
            onReady(cb: () => void): void;
            setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void>;
        }
    }
}