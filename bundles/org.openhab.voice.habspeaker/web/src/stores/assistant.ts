import { ref } from "vue";
import { defineStore } from "pinia";
import { startWebsocketWorker } from "../utils/websocket-manager";
import { WorkerInCmd } from "../utils/websocket-worker";
import { useScreenSaverStore } from "./screen-saver";
export const useAssistantStore = defineStore("assistant", () => {
  const { awakeScreenSaver, setScreenSaverTime } = useScreenSaverStore();
  // state
  const listening = ref(false);
  const speaking = ref(false);
  const online = ref(false);
  const userInteractionDone = ref(false);
  // worker actions
  function setListening(value: boolean) {
    awakeScreenSaver();
    listening.value = value;
  }
  function setSpeaking(value: boolean) {
    awakeScreenSaver();
    speaking.value = value;
  }
  function setOnline(value: boolean) {
    awakeScreenSaver();
    online.value = value;
  }
  
  let worker: Worker|null = null;
  function postToWorker(cmd: string, args = {}) {
    if (worker) {
      worker.postMessage({ cmd, ...args });
    } else {
      console.error("Worker not running");
    }
  }
  function startWorker(id:string, token:string) {
    userInteractionDone.value = true;
    if (!worker) {
      return startWebsocketWorker(id, token, {
        setListening,
        setSpeaking,
        setOnline,
        setScreenSaverTime,
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
  function resetConnection(id:string) {
    postToWorker(WorkerInCmd.RESET_CONNECTION, { id });
  }
  function renewToken(token:string) {
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
    startWorker,
    userInteractionDone,
    resetConnection,
    renewToken,
    isAudioSupported,
  };
});
