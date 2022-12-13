<script setup lang="ts">
import { onUnmounted } from 'vue';
import { useSpotifyPlayerStore } from '../../stores/media-players/spotify-player';
const spotifyPlayerStore = useSpotifyPlayerStore();
onUnmounted(()=>spotifyPlayerStore.getMediaCtrl()?.stop());
</script>
<template>
    <div class="sp-container">
        <div id="spotify-player">
            <p>TODO: spotify controllers</p>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.sp-container {
    color: white;
}
</style>
