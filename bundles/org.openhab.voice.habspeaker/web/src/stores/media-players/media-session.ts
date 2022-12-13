import { ref, Ref } from "vue";
import { defineStore } from "pinia";
import { PlaybackState } from "../../utils/websocket-manager";
import type { MediaSessionCtrl } from "../../utils/websocket-manager";
export { PlaybackState } from "../../utils/websocket-manager";
export const useMediaSessionStore = defineStore("mediaSession", () => {
    const mediaController: Ref<MediaSessionCtrl | null> = ref(null);
    const mediaState: Ref<PlaybackState> = ref(PlaybackState.STOPPED);
    return { mediaController, mediaState };
});
