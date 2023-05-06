import { ref, watch } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { PlaybackState, useMediaSessionStore } from "./media-players/media-session";
import { platform } from "../platforms";
import { ScreenSaverManager } from "../utils/screen-saver-manager";
export const useScreenSaverStore = defineStore("screenSaver", () => {
  const { mediaController, mediaState } = storeToRefs(useMediaSessionStore());
  let dimScreen = false;
  const screenSaverEnabled = ref(false);
  const manager = new ScreenSaverManager(showScreenSaver, isScreenSaverBlocked);
  manager.setSeconds(300);
  manager.bindUserEvents();
  function showScreenSaver(value: boolean) {
    screenSaverEnabled.value = value;
    if (dimScreen) {
      platform.dimDeviceScreen(value)
        .then(() => console.debug("Screen dimmed: " + value))
        .catch(err => console.error("Error setting screen brightness: ", err));
    }
  }
  function enableScreenDim(value: boolean) {
    dimScreen = value;
  }
  function isScreenSaverBlocked() {
    var mediaCtrl = mediaController.value;
    return mediaCtrl?.getAwakeScreen() || mediaState.value == PlaybackState.PLAYING;
  }
  function setScreenSaverTime(seconds: number) {
    manager.setSeconds(seconds);
  }
  function awakeScreenSaver() {
    console.debug("main: awake screen saver");
    manager.awake();
  }
  watch(mediaController, manager.awake);
  watch(mediaState, manager.awake);
  return {
    awakeScreenSaver,
    screenSaverEnabled,
    setScreenSaverTime,
    enableScreenDim,
  };
});