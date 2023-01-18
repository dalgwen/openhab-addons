import { Ref, ref } from "vue";
import { defineStore } from "pinia";
import { platform, SpeakerLocalSettings } from "../platforms";
export const useSettingsStore = defineStore("settings", () => {
  const speakerId: Ref<string | null> = ref(null);
  function getSpeakerId() {
    return platform.getSpeakerId();
  }
  function generateId() {
    return generateUUID();
  }
  async function setSpeakerSettings(settings: SpeakerLocalSettings) {
    await platform.setSpeakerSettings(settings);
  }
  function getOHUrl() {
    return platform.getUrlOpenHAB();
  }
  function getOHToken() {
    return platform.getServerToken();
  }
  return {
    getSpeakerId,
    generateId,
    getOHUrl,
    getOHToken,
    setSpeakerSettings
  };
});
function generateUUID() {
  let d = new Date().getTime(),
    d2 = (performance && performance.now && performance.now() * 1000) || 0;
  return "xxxx-xxxx-xxxx".replace(/[xy]/g, (c) => {
    let r = Math.random() * 16;
    if (d > 0) {
      r = (d + r) % 16 | 0;
      d = Math.floor(d / 16);
    } else {
      r = (d2 + r) % 16 | 0;
      d2 = Math.floor(d2 / 16);
    }
    return (c == "x" ? r : (r & 0x7) | 0x8).toString(16);
  });
}
