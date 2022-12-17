<script setup lang="ts">
import { watch, ref, Ref, onMounted } from 'vue';
import { useWebAudioPlayerStore } from '../../stores/media-players/web-audio-player';

const props = defineProps({
    mediaId: String,
});
const audioElement: Ref<HTMLAudioElement | null> = ref(null);
const store  = useWebAudioPlayerStore();
onMounted(()=> {
    if(audioElement.value) {
        store.registerMediaController(audioElement.value)
    }
});
watch(() => props.mediaId, (value) => {
    console.debug("Playing new audio ", value);
    audioElement.value?.load();
});
function getAudioType(url: string): string {
    const fileParts = url.split('/')[0]?.split('.');
    // presume mp4
    let type = "mp4";
    if (fileParts.length > 1) {
        type = fileParts[fileParts.length - 1];
    }
    return `audio/${type}`;
}
</script>
<template>
    <div class="media-container">
        <audio v-if="props.mediaId" :ref="(el) => {audioElement = el as any}" :src="props.mediaId" controls autoplay preload="auto"></audio>
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
