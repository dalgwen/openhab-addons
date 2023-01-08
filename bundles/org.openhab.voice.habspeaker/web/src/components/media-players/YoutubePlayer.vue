<script lang="ts" setup>
import { storeToRefs } from 'pinia';
import { watch, onUnmounted } from 'vue';
import { useMediaSessionStore } from '../../stores/media-players/media-session';
import { useYoutubePlayerStore } from '../../stores/media-players/youtube-player';
const mediaStore = useMediaSessionStore();
const { mediaTarget } = storeToRefs(mediaStore);
const youtubePlayerStore = useYoutubePlayerStore();
if (mediaTarget.value) {
    youtubePlayerStore.playVideo(mediaTarget.value);
}
watch(mediaTarget, (value) => {
    if (value) {
        console.debug("Playing youtube new video ", value);
        youtubePlayerStore.playVideo(value);
    }
});
onUnmounted(youtubePlayerStore.destroyPlayer);
</script>
<template>
    <div id="youtube-player-container" class="yt-container">
        <div id="youtube-player"></div>
    </div>
</template>

<style lang="scss" scoped>
.yt-container {
    width: 100%;
    height: 100%;
}
</style>
