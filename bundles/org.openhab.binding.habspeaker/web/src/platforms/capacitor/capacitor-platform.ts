import { Platform, PlatformName, SpeakerLocalSettings } from "../platform";
import { WebPlatform } from "../web/web-platform";
import { Dialog } from '@capacitor/dialog';
import { VoiceRecorder } from "capacitor-voice-recorder";
import { Preferences } from '@capacitor/preferences';
import { KeepAwake } from '@capacitor-community/keep-awake';
import { ScreenBrightness } from '@capacitor-community/screen-brightness';
class CapacitorPlatform extends WebPlatform implements Platform {
    screenDimmed = false;
    screenBrightnessBU = 1;
    getName(): Promise<PlatformName> {
        return Promise.resolve('capacitor');
    }
    async keepDeviceAwake(value: boolean): Promise<void> {
        try {
            if (!await KeepAwake.isSupported()) {
                console.warn("Keep await device is not supported");
                return;
            }
            if (value) {
                await KeepAwake.keepAwake();
            } else {
                await KeepAwake.allowSleep();
            }
        } catch (error) {
            console.error(error);
        }
    }
    async dimDeviceScreen(value: boolean): Promise<void> {
        if (value && !this.screenDimmed) {
            this.screenDimmed = true;
            this.screenBrightnessBU = (await ScreenBrightness.getBrightness()).brightness;
            await ScreenBrightness.setBrightness({ brightness: 0 });
        } else if (!value && this.screenDimmed) {
            this.screenDimmed = false;
            await ScreenBrightness.setBrightness({ brightness: this.screenBrightnessBU });
        }
    }
    async getSpeakerId(): Promise<string | null> {
        return (await Preferences.get({ key: "speakerId" }))?.value;
    }
    async getUrlOpenHAB(): Promise<string> {
        return (await Preferences.get({ key: "ohUrl" }))?.value ?? '';
    }
    getUrlLogin(): Promise<string> {
        return this.getUrlOpenHAB();
    }
    async getServerToken(): Promise<string> {
        return (await Preferences.get({ key: "ohToken" }))?.value ?? '';
    }
    async isServerTokenNeeded(): Promise<boolean> {
        return true;
    }
    async setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void> {
        await Preferences.set({ key: "speakerId", value: localSettings.speakerId });
        await Preferences.set({ key: "ohToken", value: localSettings.ohToken });
        await Preferences.set({ key: "ohUrl", value: localSettings.ohUrl });
    }
    async setup(startMic: () => Promise<void>): Promise<string | null> {
        const requestPermissionsAndStart = async () => {
            let audioPermissionStatus = (await VoiceRecorder.hasAudioRecordingPermission()).value;
            if (!audioPermissionStatus) {
                audioPermissionStatus = (await VoiceRecorder.requestAudioRecordingPermission()).value;
            }
            console.debug(`Audio permissions state: ${audioPermissionStatus}`);
            if (audioPermissionStatus) {
                if (await capacitorPlatform.getSpeakerId() != null) {
                    // auto start speaker if it is configured
                    startMic();
                }
            } else {
                await Dialog.alert({ message: "HABSpeaker requires audio permissions to work" });
                await requestPermissionsAndStart();
            }
        }
        await requestPermissionsAndStart();
        return null;
    }
    async redirectToRoot(): Promise<boolean> {
        return true;
    }
}

export const capacitorPlatform = new CapacitorPlatform();