<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useAuthStore } from "../stores/auth";
import { useAssistantStore } from "../stores/assistant";
import { useSettingsStore } from "../stores/settings";
import { useIOStore } from "../stores/io";
import router from "../router";
import AssistantWidget from "../components/AssistantWidget.vue";
import YoutubePlayer from "../components/media-players/YoutubePlayer.vue";
import SpotifyPlayer from "../components/media-players/SpotifyPlayer.vue";
import WebAudioPlayer from "../components/media-players/WebAudioPlayer.vue";
import WebVideoPlayer from "../components/media-players/WebVideoPlayer.vue";
import { MediaProvider, useMediaSessionStore } from "../stores/media-players/media-session";
const store = useAssistantStore();
const ioStore = useIOStore();
const mediaSessionStore = useMediaSessionStore();
const { getSpeakerId } = useSettingsStore();
const { getAccessToken } = useAuthStore();
const { startAssistant, isAudioSupported } = store;
const { online } = storeToRefs(ioStore);
const { mediaId, mediaProvider } = storeToRefs(mediaSessionStore);
const { userInteractionDone, miniMode } = storeToRefs(store);
async function onPanelClick() {
  if (!userInteractionDone.value) {
    startAssistant(
      await getSpeakerId(),
      getAccessToken()
    ).catch((error) => {
      console.error(error);
      router.replace("/error");
    });
    userInteractionDone.value = true;
  }
}
// Check browser audio support
if (!isAudioSupported()) {
  router.replace("/audio-error");
}
function getMediaComponent() {
  switch (mediaProvider.value) {
    case MediaProvider.YOUTUBE:
      return YoutubePlayer;
    case MediaProvider.SPOTIFY:
      return SpotifyPlayer;
    case MediaProvider.WEB_VIDEO:
      return WebVideoPlayer;
    case MediaProvider.WEB_AUDIO:
      return WebAudioPlayer;
    default:
      return null;
  }
}
</script>

<template>
  <main>
    <div @click="onPanelClick()" class="container"
      :class="{clickable: !userInteractionDone,loading: userInteractionDone && !online, 'container-mini-mode': miniMode}">
      <AssistantWidget :class="{ 'speaker-btn-mini': miniMode }" />
      <component v-if="mediaProvider" :is="getMediaComponent()" :mediaId="mediaId"></component>
    </div>
  </main>
</template>

<style lang="scss" scoped>
.container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 86vh;
  background-color: var(--color-internal-background);
}

.container-mini-mode {
  height: 84vh;
}

.speaker-btn-mini {
  position: absolute;
  bottom: -7vh;
}
</style>