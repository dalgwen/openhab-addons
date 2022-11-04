import { ref } from "vue";
import { defineStore } from "pinia";
export const useSettingsStore = defineStore("settings", () => {
  const storagePrefix = "habspeaker.ui:";
  const idLocalStorageKey = `${storagePrefix}id`;
  const labelLocalStorageKey = `${storagePrefix}label`;
  const listeningItemLocalStorageKey = `${storagePrefix}listeningItem`;
  const storedAudioComponentId = localStorage.getItem(idLocalStorageKey);
  const storedAudioComponentLabel = localStorage.getItem(labelLocalStorageKey);
  const storedListeningItem = localStorage.getItem(listeningItemLocalStorageKey);
  const audioComponentId = ref(storedAudioComponentId ?? generateUUID());
  const audioComponentLabel = ref(
    storedAudioComponentLabel ?? getDefaultLabel(audioComponentId.value)
  );
  const listeningItem = ref(storedListeningItem ?? "");
  if (storedAudioComponentId == null) {
    commit();
  }
  function commit() {
    localStorage.setItem(idLocalStorageKey, audioComponentId.value);
    localStorage.setItem(labelLocalStorageKey, audioComponentLabel.value);
    localStorage.setItem(listeningItemLocalStorageKey, listeningItem.value);
  }
  return {
    audioComponentId,
    audioComponentLabel,
    listeningItem,
    commit,
  };
});
function getDefaultLabel(id) {
  return `HAB Speaker Client (${id})`;
}
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
