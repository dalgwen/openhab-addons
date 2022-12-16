import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { MediaProvider, PlaybackState, useMediaSessionStore } from "./media-session";
import { getMediaSessionCtrl } from "./web-audio-player";
export const useWebVideoPlayerStore = defineStore("web-video", () => {
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController, mediaState } = storeToRefs(mediaSessionStore);
  var player = ref<HTMLVideoElement | null>(null);
  function registerMediaController(playerRef: HTMLVideoElement) {
    player.value = playerRef;
    mediaController.value = getMediaSessionCtrl(MediaProvider.WEB_VIDEO, playerRef, (state) => mediaState.value = state, true);
  }
  return {
    registerMediaController,
  };
});
