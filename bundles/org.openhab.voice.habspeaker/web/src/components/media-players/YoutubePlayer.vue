<script setup>
import { watch, onUnmounted } from 'vue';
import { useYoutubePlayerStore } from '../../stores/media-players/youtube-player';

const props = defineProps({
    mediaId: String,
});
const youtubePlayerStore = useYoutubePlayerStore();
if (props.mediaId && props.mediaId.length) {
    youtubePlayerStore.playVideo(props.mediaId);
}
watch(() => props.mediaId, (value) => {
    console.debug("Playing youtube new video ", value);
    youtubePlayerStore.playVideo(value);
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
