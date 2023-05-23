import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { MediaProvider, PlaybackState, useMediaSessionStore } from "./media-session";
import { getMediaSessionCtrl } from "./web-audio-player";
export const useWebVideoPlayerStore = defineStore("video-player", () => {
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController, mediaState } = storeToRefs(mediaSessionStore);
  var player = ref<HTMLVideoElement | null>(null);
  function registerMediaController(playerRef: HTMLVideoElement) {
    const controller = mediaController.value = getMediaSessionCtrl(MediaProvider.VIDEO_PLAYER, playerRef, (state) => mediaState.value = state, true);
    mediaSessionStore.getMediaVolume().then(level => controller.setVolume(level));
    player.value = playerRef;
  }
  return {
    registerMediaController,
  };
});
