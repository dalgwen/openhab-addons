import { Platform, PlatformName } from "../platform";
import { WebPlatform } from "../web/web-platform";
import { App } from '@capacitor/app';
import { Dialog } from '@capacitor/dialog';
import { AudioPermissions } from "capacitor-audio-permission";
class CapacitorPlatform extends WebPlatform implements Platform {
    getName(): PlatformName {
        return 'capacitor';
    }
    async getUrlOpenHAB(): Promise<string> {
        return `http://192.168.1.200:8080`;
    }
    async setup(startMic: () => void): Promise<void> {
        let audioPermissionStatus = (await AudioPermissions.checkPermissions()).audio;
        console.log(`Audio permissions state: ${audioPermissionStatus}`);
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