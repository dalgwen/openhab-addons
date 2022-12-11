<script setup>
import { RouterLink, RouterView } from "vue-router";
import { useAuthStore } from "./stores/auth";
import ScreenSaver from "./components/ScreenSaver.vue";
import { useSpotifyPlayerStore } from "./stores/media-players/spotify-player";
import { useAssistantStore } from "./stores/assistant";
import { storeToRefs } from "pinia";
const { getUIConfig, authorize } = useAuthStore();
const { miniMode } = storeToRefs(useAssistantStore());
const spotifyStore = useSpotifyPlayerStore();
getUIConfig().then(async ({ secure, spotifyEnabled, label }) => {
  if (secure) {
    console.debug("Authorization required!");
    await authorize();
  } else {
    console.debug("Authorization no required!");
  }
  if (spotifyEnabled) {
    spotifyStore.initPlayer(label)
      .then(console.log("Spotify initialized"))
      .catch(err => console.error("Spotify error: ", err));
  }
});
</script>

<template>
  <ScreenSaver></ScreenSaver>
  <header>
    <div class="title">
      <span>HAB </span>
      <span class="grey">Speaker</span>
    </div>
    <nav>
      <RouterLink to="/">Speaker</RouterLink>
      <RouterLink to="/Settings">Settings</RouterLink>
    </nav>
  </header>

  <RouterView />
  <div class="logo-container" :class="{'logo-container-mini': miniMode}">
    <img alt="openHAB logo" class="logo" src="@/assets/openhab-logo.svg" width="125" height="48" />
  </div>
</template>

<style scoped>
header {
  position: relative;
  line-height: 1.5;
  max-height: 100vh;
}

.title {
  position: absolute;
  font-size: 30px;
  color: #e64a19;
  top: -0.3rem;
  width: 176px;
  height: 46px;
  left: 50%;
  margin-left: -88px;
  text-align: center;
  line-height: 46px;
  display: inline-flex;
  user-select: none;
  user-select: none;
  -webkit-user-select: none;
}

.title .grey {
  color: #474747;
  font-family: Chalkboard SE;
  line-height: 40px;
  margin-left: 6px;
}

@media (orientation: portrait) {
  .title {
    left: 0%;
    margin-left: 0px;
  }
}

.logo-container {
  padding: 3.5vh;
  -webkit-user-select: none;
  user-select: none;
}
.logo-container img {
  position: absolute;
  right: 0;
  bottom: 0;
}
.logo-container-mini {
  padding: 4vh;
}
nav {
  position: relative;
  width: 100%;
  text-align: right;
}

nav a.router-link-exact-active {
  color: var(--color-text);
  display: none;
}

nav a {
  display: inline-block;
  padding: 0.5rem 1rem;
}

nav a:first-of-type {
  border: 0;
}
</style>
