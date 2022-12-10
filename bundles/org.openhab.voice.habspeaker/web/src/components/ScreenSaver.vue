<script setup>
import { storeToRefs } from "pinia";
import { ref, onUnmounted, watch } from "vue";
import { useScreenSaverStore } from "../stores/screen-saver";
const screenSaverStore = useScreenSaverStore();
const { screenSaverEnabled } = storeToRefs(screenSaverStore);
const logo = ref(null);
function moveIcon() {
    function getRandomArbitrary(min, max) {
        return Math.round((Math.random() * (max - min) + min) * 100) / 100
    }
    const logoHtmlElement = logo.value;
    if (logoHtmlElement) {
        logoHtmlElement.style.top = getRandomArbitrary(5, 95) + "%";
        logoHtmlElement.style.left = getRandomArbitrary(10, 90) + "%";
    }
}
let iconMovementInterval;
function setupIconMovement() {
    if (screenSaverEnabled.value) {
        iconMovementInterval = setInterval(moveIcon, 5000);
    } else {
        clearInterval(iconMovementInterval);
    }
}
watch(screenSaverEnabled, setupIconMovement);
setupIconMovement();
onUnmounted(() => {
    clearInterval(iconMovementInterval);
});
</script>
<template>
    <div v-if="screenSaverEnabled" class="background">
        <img alt="openHAB logo" src="@/assets/openhab-logo.svg" width="125" height="48" ref="logo" unselectable="on" />
    </div>
</template>

<style lang="scss" scoped>
.background {
    position: absolute;
    display: block;
    background-color: black;
    width: 100%;
    height: 100%;
    z-index: 999;
    top: 0;
    left: 0;
    user-select: none;
}

.background img {
    background-color: black;
    position: absolute;
    top: 50%;
    left: 50%;
    margin-left: -62.5px;
    z-index: 999;
    margin-top: -24px;
    -moz-user-select: none;
    -webkit-user-select: none;
    user-select: none;
    cursor: default;
}
</style>
