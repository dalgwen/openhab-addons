<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { onUnmounted, watch } from 'vue';
import { PlaybackState, useMediaSessionStore } from '../../stores/media-players/media-session';
import { useSpotifyPlayerStore } from '../../stores/media-players/spotify-player';
const mediaSessionStore = useMediaSessionStore();
const { mediaState } = storeToRefs(mediaSessionStore);
const spotifyPlayerStore = useSpotifyPlayerStore();
const { songImg, songName } = storeToRefs(spotifyPlayerStore);
let isPlaying = false;
watch(mediaState, value => isPlaying = (value == PlaybackState.PLAYING));
onUnmounted(() => spotifyPlayerStore.getMediaCtrl()?.then(pl => pl?.stop()));
async function play() {
    (await spotifyPlayerStore.getMediaCtrl())?.play();
}
async function pause() {
    (await spotifyPlayerStore.getMediaCtrl())?.pause();
}
async function next() {
    (await spotifyPlayerStore.getMediaCtrl())?.next();
}
async function previous() {
    (await spotifyPlayerStore.getMediaCtrl())?.previous();
}
</script>
<template>
    <div class="sp-container">
        <div class="sp-player">
            <div class="center-content">
                <div class="center-pic">
                    <img v-if="songImg.length" :src="songImg" id="song-pic">
                </div>
                <div class="controls-wrapper">
                    <div class="title">
                        <a href="#">{{ songName }}</a>
                    </div>
                    <div class="controls">
                        <i class='fas fa-random'></i>
                        <div class="controls-center">
                            <fa-icon class="item" icon="fa-solid fa-fast-backward" @click="previous" />
                            <fa-icon v-if="isPlaying" icon="fa-solid fa-pause" @click="pause" class="item" />
                            <fa-icon v-else class="item" icon="fa-solid fa-play" @click="play" />
                            <fa-icon class="item" icon="fa-solid fa-fast-forward" @click="next" />
                        </div>
                        <i class='fas fa-expand-arrows-alt'></i>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style lang="scss" scoped>
.sp-container {
    color: white;
    background: linear-gradient(120DEG, #1DB954, #191414);
    width: 100%;
    height: 100%;
    text-align: center;
}

.sp-player {
    height: 100%;
    padding: 6vh;
}

.topnav span {
    padding: 18px;
    color: #fff;
    animation: maintext 0.9s ease-in-out infinite;
}

@keyframes maintext {
    0% {
        color: #e58e73;
    }

    100% {
        color: #e58e73;
    }
}

#small-text {
    font-size: 0.8em;
    color: #CCC;
    display: inline-block;
    animation: textanimation 0.9s ease-in-out both;
}

@keyframes textanimation {
    0% {
        letter-spacing: -0.5rem;
    }

    40% {
        opacity: 0.6;
    }

    100% {
        opacity: 1;
    }
}

#logo {
    margin-left: 0;
    width: 8vw;
}

.center-content {
    min-width: 17em;
    display: inline-block;
    padding: 15px;
    background: #1e272c;
    box-shadow: 20px 20px 60px #1a2125, -20px -20px 60px #232d33;
}

.center-pic {
    margin: 10px;
    height: 45vh;
}

#song-pic {
    height: 100%;
    width: 100%;
}

.controls-wrapper {
    margin: 10px;
}

.title {
    width: 100%;
    height: auto;
    padding: 5px;
    display: flex;
    justify-content: space-between;
    flex-direction: row;
}

.title a {
    color: #fff;
    font-size: 0.8em;
    text-decoration: none;
}

#heart {
    transition: all .2s ease;
}

#heart:hover {
    color: #1ed760;
    transform: scale(1.1);
}

.controls {
    height: 10vh;
    justify-content: space-between;
    display: flex;
    align-items: center;
    margin: 0 auto;
}

.controls .item {
    font-size: 20px;
    margin: 2vh;
    color: #CCC;
    cursor: pointer;
}

.controls-center {
    display: inline-block;
    justify-content: center;
}
</style>
