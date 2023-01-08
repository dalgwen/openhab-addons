<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { watch, ref, Ref, onMounted } from 'vue';
import { useMediaSessionStore } from '../../stores/media-players/media-session';
import { useWebAudioPlayerStore } from '../../stores/media-players/web-audio-player';
const audioElement: Ref<HTMLAudioElement | undefined> = ref();
const mediaStore = useMediaSessionStore();
const { mediaTarget } = storeToRefs(mediaStore);
const store = useWebAudioPlayerStore();
onMounted(() => {
    if (audioElement.value) {
        store.registerMediaController(audioElement.value)
    }
});
watch(mediaTarget, (value) => {
    console.debug("Playing new audio ", value);
    audioElement.value?.load();
});
</script>
<template>
    <div class="media-container">
        <audio v-if="mediaTarget" :ref="(el) => { audioElement = el as any }" :src="mediaTarget.mediaId" controls
            autoplay preload="auto"></audio>
    </div>
</template>

<style lang="scss" scoped>
.media-container {
    height: 100%;
    width: 100%;
    background-color: black;
}

.media-container audio {
    width: 100%;
    height: 100%;
}
</style>
