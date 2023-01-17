/// <reference types="vite/client" />
import type { Platform } from "./platform";
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
export async function getPlatformName() {
    return (await getPlatform()).getName();
}
export async function setupPlatform(onReady: () => void) {
    const platform = await getPlatform();
    console.info(`main: running ${platform.getName()} setup`);
    return platform.setup(onReady);
}
export async function getSpotifyCtrl() {
    return (await getPlatform()).getSpotifyCtrl();
}

export async function getUrlOpenHAB() {
    return (await getPlatform()).getUrlOpenHAB();
}

export async function getSpeakerId() {
    return (await getPlatform()).getSpeakerId();
}
export async function getServerToken() {
    return (await getPlatform()).getServerToken();
} 