<script setup>
import { storeToRefs } from "pinia";
import { useAuthStore } from "../stores/auth";
import { useAssistantStore } from "../stores/assistant";
import { useSettingsStore } from "../stores/settings";
import router from "../router";
import AssistantWidget from "../components/AssistantWidget.vue";
const store = useAssistantStore();
const settingsStore = useSettingsStore();
const { getAccessToken } = useAuthStore();
const { startWorker, isAudioSupported } = store;
const { online, userInteractionDone } = storeToRefs(store);
const { audioComponentId } = storeToRefs(settingsStore);
function onPanelClick() {
  if (!userInteractionDone.value) {
    startWorker(
      audioComponentId.value,
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
</script>

<template>
  <main>
    <div @click="onPanelClick()" class="container"
      :class="{clickable: !userInteractionDone,loading: userInteractionDone && !online}">
      <AssistantWidget />
    </div>
  </main>
</template>

<style lang="scss" scoped>
.container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 86vh;
  background-color: var(--color-internal-background);
}
</style>