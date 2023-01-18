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
import { platform } from "../platforms";

const store = useAssistantStore();
const ioStore = useIOStore();
const mediaSessionStore = useMediaSessionStore();
const { getSpeakerId } = useSettingsStore();
const { getAccessToken } = useAuthStore();
const { startAssistant, isAudioSupported } = store;
const { online } = storeToRefs(ioStore);
const { mediaProvider } = storeToRefs(mediaSessionStore);
const { userInteractionDone, miniMode } = storeToRefs(store);
async function startSpeaker() {
  if (!userInteractionDone.value) {
    const id = await getSpeakerId();
    if (!id) {
      console.error("Unable to load speaker id");
      return;
    }
    startAssistant(
      id,
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
platform.setup(startSpeaker)
  .then(() => console.debug("main: platform setup done"))
  .catch(err => console.error("main: error on platform setup.", err));
</script>

<template>
  <main>
    <div @click="startSpeaker()" class="container"
      :class="{clickable: !userInteractionDone,loading: userInteractionDone && !online, 'container-mini-mode': miniMode}">
      <AssistantWidget :class="{ 'speaker-btn-mini': miniMode }" />
      <YoutubePlayer v-if="mediaProvider == MediaProvider.YOUTUBE"></YoutubePlayer>
      <SpotifyPlayer v-else-if="mediaProvider == MediaProvider.SPOTIFY"></SpotifyPlayer>
      <WebAudioPlayer v-else-if="mediaProvider == MediaProvider.WEB_AUDIO"></WebAudioPlayer>
      <WebVideoPlayer v-else-if="mediaProvider == MediaProvider.WEB_VIDEO"></WebVideoPlayer>
    </div>
  </main>
</template>

<style lang="css" scoped>
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