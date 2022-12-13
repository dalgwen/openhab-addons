<script setup lang="ts">
import { watch, ref, Ref, onMounted } from 'vue';
import { useWebVideoPlayerStore } from '../../stores/media-players/web-video-player';

const props = defineProps({
    mediaId: String,
});
const videoElement: Ref<HTMLVideoElement | null> = ref(null);
const store  = useWebVideoPlayerStore();
onMounted(()=> {
    if(videoElement.value) {
        store.registerMediaController(videoElement.value)
        console.log(videoElement.value);
    }
});
watch(() => props.mediaId, (value) => {
    console.log("Playing new video ", value);
    videoElement.value?.load();
});
function getVideoType(url: string): string {
    const fileParts = url.split('/')[0]?.split('.');
    // presume mp4
    let type = "mp4";
    if (fileParts.length > 1) {
        type = fileParts[fileParts.length - 1];
    }
    return `video/${type}`;
}
</script>
<template>
    <div class="media-container">
        <video :ref="(el) => {videoElement = el as any}" controls autoplay preload="auto" playsinline>
            <source v-if="props.mediaId" :src="props.mediaId" />
        </video>
    </div>

</template>

<style lang="scss" scoped>
.media-container {
    height: 100%;
    width: 100%;
    background-color: black;
}
.media-container video {
    width: 100%;
    height: 100%;
}
</style>
