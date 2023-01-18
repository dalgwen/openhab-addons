import { Platform, PlatformName, SpeakerLocalSettings } from "../platform";
import { WebPlatform } from "../web/web-platform";
import { App } from '@capacitor/app';
import { Dialog } from '@capacitor/dialog';
import { AudioPermissions } from "capacitor-audio-permission";
import { Preferences } from '@capacitor/preferences';

class CapacitorPlatform extends WebPlatform implements Platform {
    getName(): PlatformName {
        return 'capacitor';
    }
    async getSpeakerId(): Promise<string | null> {
        return (await Preferences.get({ key: "speakerId" }))?.value;
    }
    async getUrlOpenHAB(): Promise<string> {
        return (await Preferences.get({ key: "ohUrl" }))?.value ?? '';
    }
    async getServerToken(): Promise<string> {
        return (await Preferences.get({ key: "ohToken" }))?.value ?? '';
    }
    async shouldRedirectToLogin(): Promise<boolean> {
        return true;
    }
    async setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void> {
        await Preferences.set({ key: "speakerId", value: localSettings.speakerId });
        await Preferences.set({ key: "ohToken", value: localSettings.ohToken });
        await Preferences.set({ key: "ohUrl", value: localSettings.ohUrl });
    }
    async setup(startMic: () => void): Promise<void> {
        let audioPermissionStatus = (await AudioPermissions.checkPermissions()).audio;
        console.debug(`Audio permissions state: ${audioPermissionStatus}`);
        if (audioPermissionStatus == 'granted') {
            return;
        }
        audioPermissionStatus = (await AudioPermissions.requestPermissions()).audio;
        if (audioPermissionStatus == 'granted') {
            return;
        }
        await Dialog.alert({ message: "HAB Speaker requires audio permissions to work" });
        await App.exitApp();
    }
}

export const capacitorPlatform = new CapacitorPlatform();