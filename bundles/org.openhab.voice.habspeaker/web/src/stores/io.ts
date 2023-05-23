import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { useScreenSaverStore } from "./screen-saver";
import { PlaybackState, useMediaSessionStore } from "./media-players/media-session";
import { MediaStateCmd, WorkerOutCmd, WorkerOutCmdType } from "../utils/io-types";
import { useSettingsStore } from "./settings";
import { platform } from "../platforms";
import { IOMain, IOCallbacks } from "../utils/io-main";

export const useIOStore = defineStore("io", () => {
  let speakerLabel = ref("HAB Speaker");
  const { getOHUrl } = useSettingsStore();
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController } = storeToRefs(mediaSessionStore);
  const { awakeScreenSaver, setScreenSaverTime, enableScreenDim } = useScreenSaverStore();
  // state
  let ioMain: IOMain | null = null;
  const listening = ref(false);
  const speaking = ref(false);
  const online = ref(false);

  // worker actions
  function setListening(value: boolean) {
    awakeScreenSaver();
    mediaSessionStore.muteMediaVolume(value);
    listening.value = value;
  }
  function setSpeaking(value: boolean) {
    awakeScreenSaver();
    mediaSessionStore.muteMediaVolume(value);
    speaking.value = value;
  }
  function setOnline(value: boolean) {
    awakeScreenSaver();
    online.value = value;
    if (!value) {
      mediaSessionStore.stopMedia();
    }
  }
  /**
   * 
   */
  async function runMediaCommand(mediaCommandData: WorkerOutCmdType<WorkerOutCmd.MEDIA_COMMAND>) {
    try {
      if (!online.value) {
        console.warn("main: device not online, aborting media command " + mediaCommandData.type);
        return;
      }
      console.debug("main: running media command" + mediaCommandData.type);
      if ('start' === mediaCommandData.type) {
        mediaSessionStore.startMedia(mediaCommandData.provider, {
          mediaId: mediaCommandData.mediaId,
          playlistId: mediaCommandData.playlistId,
          playlistIndex: mediaCommandData.playlistIndex,
          startSecond: mediaCommandData.second,
        });
        return;
      }
      if ('claim' === mediaCommandData.type) {
        mediaSessionStore.claimMedia(mediaCommandData.provider);
        return;
      }
      const mediaSessionCtrl = mediaController.value;
      if (!mediaSessionCtrl) {
        console.warn("main: media is not started");
        return;
      }
      switch (mediaCommandData.type) {
        case 'play':
          mediaSessionCtrl.play();
          break;
        case 'pause':
          mediaSessionCtrl.pause();
          break;
        case 'stop':
          mediaSessionCtrl.getPlaybackState().then((state) => {
            if (state === PlaybackState.PLAYING) {
              mediaSessionCtrl.stop();
            }
            mediaSessionStore.stopMedia();
          });
          break;
        case 'next':
          mediaSessionCtrl.next();
          break;
        case 'previous':
          mediaSessionCtrl.previous();
          break;
        case 'seek':
          mediaSessionCtrl.seek(mediaCommandData.second);
          break;
        case 'volume':
          mediaSessionStore.setMediaVolume(mediaCommandData.level);
          break;
        default:
          console.error("Unsupported media command: ", mediaCommandData);
      }
    } catch (error) {
      console.error("Error while running media command: ", error);
    }
  }
  const ioCallbacks: IOCallbacks = {
    onConnected() {
      setOnline(true);
    },
    onDisconnected() {
      setOnline(false);
    },
    onConfigured: (config) => {
      const { screenSaverTime, dimScreen, keepAwake } = config;
      if (screenSaverTime != null && !isNaN(screenSaverTime)) {
        setScreenSaverTime(screenSaverTime);
      }
      enableScreenDim(!!dimScreen);
      platform.keepDeviceAwake(!!keepAwake);
    },
    onMediaCommand(command) {
      runMediaCommand(command).catch(err => console.error(err));
    },
    onStartListening() {
      setListening(true);
    },
    onStopListening() {
      setListening(false);
    },
    onStartSpeaking() {
      setSpeaking(true);
    },
    onStopSpeaking() {
      setSpeaking(false);
    },
  };
  async function init(id: string, ohToken: string | null) {
    const ohUrl = await getOHUrl();
    ioMain = new IOMain(ohUrl, ioCallbacks);
    await ioMain.initialize(id, ohToken);
  }

  function sendSpot() {
    ioMain?.sendSpot();
  }
  function resetConnection(id: string) {
    ioMain?.resetConnection(id);
  }

  function setAuthToken(accessToken: string) {
    ioMain?.setAuthToken(accessToken);
  }
  function sendMediaState(state: MediaStateCmd) {
    ioMain?.sendMediaState(state);
  }
  return {
    listening,
    online,
    speaking,
    init,
    sendSpot,
    resetConnection,
    sendMediaState,
    setAuthToken,
  };
});
