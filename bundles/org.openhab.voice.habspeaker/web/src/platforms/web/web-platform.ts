import { Platform, PlatformName, SpeakerLocalSettings } from "../platform";

const storagePrefix = "habspeaker.ui:";
const idLocalStorageKey = `${storagePrefix}id`;
export class WebPlatform implements Platform {
    wakeLock?: WakeLockSentinel;
    getName(): PlatformName {
        return 'web';
    }
    async keepDeviceAwake(value: boolean): Promise<void> {
        if (value && this.wakeLock == null) {
            if (typeof window.navigator === 'undefined' || typeof window.navigator.wakeLock === 'undefined') {
                console.warn("Device wake lock is not supported by this browser.")
                return;
            }
            try {
                this.wakeLock = await navigator.wakeLock.request('screen');
            } catch (err: any) {
                console.error(`Unable to keep device awake: ${err.name}, ${err.message}`);
            }
        } else if (!value && this.wakeLock != null) {
            const wakeLock = this.wakeLock;
            this.wakeLock = undefined;
            await wakeLock.release();
        }
    }
    async dimDeviceScreen(value: boolean): Promise<void> {
        if (value) {
            console.warn("Device brightness control is not supported on this platform.");
        }
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
}

export const webPlatform = new WebPlatform();