import { onUnmounted, ref, watch } from "vue";
import { defineStore } from "pinia";
const USER_INPUT_EVENTS = [
  'click', 'contextmenu', 'auxclick', 'dblclick',
  'mouseup', 'pointerup', 'touchend', 'keyup'
];
export const useScreenSaverStore = defineStore("screenSaver", () => {
  let screenSaverTime = 120;
  let screenSaverTimeout: any = null;
  const screenSaverEnabled = ref(false);
  function configureScreenSaver() {
    if (screenSaverTime > 0) {
      awakeScreenSaver();
    } else {
      disableScreenSaver();
    }
  }
  function disableScreenSaver() {
    if(screenSaverEnabled.value) screenSaverEnabled.value = false;
    if (screenSaverTimeout) clearTimeout(screenSaverTimeout);
  }
  function awakeScreenSaver() {
    disableScreenSaver();
    if (screenSaverTime > 0) {
      screenSaverTimeout = setTimeout(() => {
        screenSaverEnabled.value = true;
        screenSaverTimeout = null;
        screenSaverEnabled.value = true;
      }, screenSaverTime * 1000);
    }
  }
  function setScreenSaverTime(seconds: number) {
    screenSaverTime = seconds;
    configureScreenSaver();
  }
  USER_INPUT_EVENTS.forEach((eventName) => {
    window.addEventListener(eventName, awakeScreenSaver);
  });
  watch(() => screenSaverTime, configureScreenSaver);
  configureScreenSaver();
  onUnmounted(()=>{
    USER_INPUT_EVENTS.forEach((eventName) => {
      window.removeEventListener(eventName, awakeScreenSaver);
    });
    disableScreenSaver();
  });
  return {
    awakeScreenSaver,
    screenSaverEnabled,
    setScreenSaverTime,
  };
});