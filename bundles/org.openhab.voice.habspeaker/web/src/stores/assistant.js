import { ref } from "vue";
import { defineStore } from "pinia";
import { startWebsocketWorker } from "../utils/websocket-manager";
import { WorkerInCmd } from "../utils/websocket-worker";
export const useAssistantStore = defineStore("assistant", () => {
  // state
  const listening = ref(false);
  const speaking = ref(false);
  const online = ref(false);
  const userInteractionDone = ref(false);
  // worker actions
  function setListening(value) {
    listening.value = value;
  }
  function setSpeaking(value) {
    speaking.value = value;
  }
  function setOnline(value) {
    online.value = value;
  }
  /**@type {Worker} */
  let worker = null;

  function postToWorker(cmd, args = {}) {
    if (worker) {
      worker.postMessage({ cmd, ...args });
    } else {
      console.error("Worker not running");
    }
  }
  function startWorker(id, token) {
    userInteractionDone.value = true;
    if (!worker) {
      return startWebsocketWorker(id, token, {
        setListening,
        setSpeaking,
        setOnline,
      }).then((_worker) => {
        worker = _worker;
        console.info("worker running");
        return worker;
      });
    }
    return Promise.resolve(worker);
  }
  // component actions
  function startListening() {
    postToWorker(WorkerInCmd.ON_SPOT);
  }
  // TODO: allow manual abort?
  function stopListening() {
  }
  function resetConnection(id) {
    postToWorker(WorkerInCmd.RESET_CONNECTION, { id });
  }
  function renewToken(token) {
    if (worker) {
      postToWorker(WorkerInCmd.TOKEN_RENEW, { token });
    }
  }
  function isAudioSupported() {
    const getUserMediaSupported =
      window.navigator &&
      window.navigator.mediaDevices &&
      window.navigator.mediaDevices.getUserMedia;
    return AudioContext && getUserMediaSupported;
  }
  return {
    listening,
    online,
    speaking,
    startListening,
    stopListening,
    startWorker,
    userInteractionDone,
    resetConnection,
    renewToken,
    isAudioSupported,
  };
});
