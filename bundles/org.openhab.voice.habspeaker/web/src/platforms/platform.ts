export type PlatformName = 'web' | 'electron' | 'capacitor';

export interface Platform {
    getName(): PlatformName;
    getServerToken(): Promise<string | null>;
    getSpeakerId(): Promise<string | null>;
    getUrlOpenHAB(): Promise<string>;
    dimDeviceScreen(value: boolean): Promise<void>;
    keepDeviceAwake(value: boolean): Promise<void>;
    setLocalSettings(localSettings: SpeakerLocalSettings): Promise<void>;
    setup(startMic: () => Promise<void>): Promise<void>;
    isServerTokenNeeded(): Promise<boolean>;
}
export type SpeakerLocalSettings = {
    speakerId: string,
    ohToken?: string,
    ohUrl?: string,
}
