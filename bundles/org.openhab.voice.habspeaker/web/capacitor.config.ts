import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'org.givimad.habspeaker',
  appName: 'HABSpeaker',
  webDir: 'dist',
  bundledWebRuntime: false,
  includePlugins: [
    "@capacitor/preferences",
    "@capacitor/dialog",
    "@capacitor-community/screen-brightness",
    "@capacitor-community/keep-awake",
    "capacitor-voice-recorder"
  ],
};

export default config;
