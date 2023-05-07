<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useAssistantStore } from "../stores/assistant";
import { useIOStore } from "../stores/io";
import { ref, watch } from "vue";
const store = useAssistantStore();
const { startListening } = store;
const { listening, speaking, online } = storeToRefs(useIOStore());
const { userInteractionDone, miniMode } = storeToRefs(store);
const ring = ref<HTMLButtonElement | null>(null);
watch(listening, (value) => {
  if (!ring.value) {
    return;
  }
  if (value) {
    ring.value.classList.add("pulsate");
  } else {
    ring.value.classList.remove("pulsate");
  }
});
</script>
<template>
  <button id="speech" :disabled="!online" @click="startListening" :class="{ 'mic-btn-mini': miniMode }"
    class="mic-btn">
    <div v-if="online" ref="ring" class="pulse-ring"></div>
    <div v-if="userInteractionDone && !online" class="lds-ring">
      <div></div>
      <div></div>
      <div></div>
      <div></div>
    </div>
    <div class="microphone-container">
      <span class="led"></span>
      <span class="microphone speaker"></span>
      <span class="leg"></span>
      <span class="support"></span>
    </div>
  </button>
</template>

<style lang="css" scoped>
.clickable {
  cursor: pointer;
}

.mic-btn {
  border: none;
  padding: 0;
  border-radius: 100%;
  width: 100px;
  height: 100px;
  font-size: 3em;
  color: var(--color-microphone);
  padding: 0;
  margin: 0;
  background: var(--color-assistant);
  position: relative;
  z-index: 1;
  display: inline-block;
  line-height: 100px;
  text-align: center;
  white-space: nowrap;
  vertical-align: middle;
  -ms-touch-action: manipulation;
  touch-action: manipulation;
  cursor: pointer;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  user-select: none;
  background-image: none;
}

.mic-btn-mini {
  width: 4.3vh;
  height: 4.3vh;
}

.pulse-ring {
  content: "";
  width: 100%;
  height: 100%;
  background: transparent;
  border: 5px solid var(--color-assistant);
  border-radius: 50%;
  position: absolute;
  top: 0px;
  left: 0px;
  -webkit-transform: scale(1.9, 1.9);
}

.mic-btn-mini .pulse-ring {
  border: 3px solid var(--color-assistant);
}

@-webkit-keyframes pulsate {
  0% {
    -webkit-transform: scale(1, 1);
    opacity: 1;
  }

  100% {
    -webkit-transform: scale(1.9, 1.9);
    opacity: 0;
  }
}

.pulsate {
  animation: pulsate infinite 1.5s;
}

.microphone-container {
  background-color: transparent;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 86%;
}


.speaker-container .speaker {
  box-sizing: border-box;
  display: inline-block;
  background: var(--color-microphone);
  background-clip: content-box;
  width: 35% !important;
  height: 2.2em !important;
  border: 1em solid transparent;
  border-radius: 50%;
  border-right-color: var(--color-microphone);
  position: absolute;
  top: 50%;
  transform: translatey(-50%);
  left: -17%;
}

.speaker-container .speaker:after,
.speaker-container .speaker:before {
  content: "";
  width: 7px;
  background: var(--vt-c-openhab-red);
  border-right: 1px solid var(--vt-c-openhab-red);
  border-radius: 12px;
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
}

.speaker-container .speaker:before {
  left: 240%;
  height: 15px;
  width: 10px;
  background-color: var(--color-microphone);
}

.speaker-container .led,
.speaker-container .leg,
.speaker-container .support {
  display: none;
}


.microphone-container .microphone {
  background-color: var(--color-microphone);
  border-radius: 100px;
  width: 30%;
  height: 70%;
}

.microphone-container .leg {
  background-color: var(--color-microphone);
  width: 5%;
  height: 20%;
}

.microphone-container .support {
  background-color: var(--color-microphone);
  width: 30%;
  height: 5%;
}

.microphone-container .led {
  z-index: 2;
  background-color: var(--color-assistant);
  border-radius: 50px;
  position: relative;
  top: 30%;
  width: 5%;
  height: 15%;
}

.loading .microphone-container .led {
  background: var(--color-assistant);
}

.lds-ring {
  display: inline-block;
  position: absolute;
  width: 136%;
  height: 136%;
  top: -18%;
  left: -18%;
}

.lds-ring div {
  box-sizing: border-box;
  display: block;
  position: absolute;
  width: 86%;
  height: 86%;
  margin: 7%;
  border: 8px solid var(--color-assistant);
  border-radius: 50%;
  animation: lds-ring 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
  border-color: var(--color-assistant) transparent transparent transparent;
}

.lds-ring div:nth-child(1) {
  animation-delay: -0.45s;
}

.lds-ring div:nth-child(2) {
  animation-delay: -0.3s;
}

.lds-ring div:nth-child(3) {
  animation-delay: -0.15s;
}

@keyframes lds-ring {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}
</style>
