import { Platform, PlatformName, SpeakerLocalSettings } from "../platform";

const storagePrefix = "habspeaker.ui:";
const idLocalStorageKey = `${storagePrefix}id`;
export class WebPlatform implements Platform {
    private runningSetup = false;
    private wakeLock?: WakeLockSentinel;
    getName(): Promise<PlatformName> {
        return Promise.resolve('web');
    }
    async keepDeviceAwake(value: boolean): Promise<void> {
        if (value && this.wakeLock == null) {
            if (typeof window.navigator === 'undefined' || typeof window.navigator.wakeLock === 'undefined') {
                console.warn("Device wake lock is not supported by this browser.")
                return;
            }
            try {
                this.wakeLock = await navigator.wakeLock.request('screen');
            } catch (err: unknown) {
                console.error("Unable to keep device awake: ", err);
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
    async isServerTokenNeeded(): Promise<boolean> {
        return false;
    }
    async getServerToken(): Promise<string | null> {
        // nothing to do
        return null;
    }
    async setup(startMic: () => Promise<void>): Promise<string | null> {
        const wrapper = async () => {
            if (this.runningSetup) {
                return;
            }
            this.runningSetup = true;
            try {
                await startMic();
                document.removeEventListener("click", wrapper);
            } catch (error) {
                console.error("Error starting IO:", error);
            } finally {
                this.runningSetup = false;
            }
        };
        document.addEventListener("click", wrapper);
        console.debug("Waiting click event at document.");
        return "Click outside the widget to start the speaker";
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
    getUrlLogin(): Promise<string> {
        return this.getUrlOpenHAB();
    }
    async redirectToRoot(): Promise<boolean> {
        if (import.meta.env.VITE_DEV_SERVER_URL) {
            return true;
        }
        return false;
    }
}

export const webPlatform = new WebPlatform();