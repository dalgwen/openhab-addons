<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { watch, ref, Ref, onMounted } from 'vue';
import { useMediaSessionStore } from '../../stores/media-players/media-session';
import { useWebVideoPlayerStore } from '../../stores/media-players/web-video-player';
const videoElement: Ref<HTMLVideoElement | undefined> = ref();
const mediaStore = useMediaSessionStore();
const { mediaTarget } = storeToRefs(mediaStore);
const store = useWebVideoPlayerStore();
onMounted(() => {
    if (videoElement.value) {
        store.registerMediaController(videoElement.value)
    }
});
watch(mediaTarget, (value) => {
    console.debug("Playing new video ", value?.mediaId);
    videoElement.value?.load();
});
</script>
<template>
    <div class="media-container">
        <video :ref="(el) => { videoElement = el as any }" controls autoplay preload="auto" playsinline>
            <source v-if="mediaTarget" :src="mediaTarget.mediaId" />
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
