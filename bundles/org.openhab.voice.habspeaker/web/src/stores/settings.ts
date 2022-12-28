import { Ref, ref } from "vue";
import { defineStore } from "pinia";
import { getSpeakerId as getPlatformSpeakerId } from "../platforms";
export const useSettingsStore = defineStore("settings", () => {
  const storagePrefix = "habspeaker.ui:";
  const idLocalStorageKey = `${storagePrefix}id`;
  const speakerId: Ref<string | null> = ref(null);
  async function getSpeakerId() {
    if (speakerId.value === null) {
      const storedAudioComponentId = await getPlatformSpeakerId();
      speakerId.value = storedAudioComponentId ?? generateUUID();
      if (storedAudioComponentId == null) {
        setSpeakerId(speakerId.value);
      }
    }
    return speakerId.value;
  }
  function setSpeakerId(id: string) {
    localStorage.setItem(idLocalStorageKey, id);
    speakerId.value = id;
  }
  return {
    getSpeakerId,
    setSpeakerId,
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
