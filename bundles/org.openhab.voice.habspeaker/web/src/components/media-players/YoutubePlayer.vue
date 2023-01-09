<script lang="ts" setup>
import { storeToRefs } from 'pinia';
import { watch, onUnmounted, onMounted, Ref, ref } from 'vue';
import { useMediaSessionStore } from '../../stores/media-players/media-session';
import { useYoutubePlayerStore } from '../../stores/media-players/youtube-player';
const mediaStore = useMediaSessionStore();
const { mediaTarget } = storeToRefs(mediaStore);
const youtubePlayerStore = useYoutubePlayerStore();
const youtubeContainer: Ref<HTMLElement | undefined> = ref();
watch(mediaTarget, (value) => {
    if (value) {
        console.debug("Playing youtube new video ", value);
        youtubePlayerStore.playVideo(value);
    }
});
onMounted(()=> {
    console.debug("main: youtube player mounted");
    if(youtubeContainer.value) {
        youtubeContainer.value.innerHTML = '<div id="youtube-player"></div>';
    }
    if (mediaTarget.value) {
        youtubePlayerStore.playVideo(mediaTarget.value);
    }
});
onUnmounted(youtubePlayerStore.destroyPlayer);
</script>
<template>
    <div :ref="(el) => { youtubeContainer = el as any }" id="youtube-player-container" class="yt-container">
    </div>
</template>

<style lang="scss" scoped>
.yt-container {
    width: 100%;
    height: 100%;
}
</style>
