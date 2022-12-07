import { WebAudioSink } from "./web-sink";
import { WebAudioSource } from "./web-source";
import { WorkerInCmd, WorkerOutCmd } from "./websocket-worker";
/**@type {AudioContext} */
let audioContext: AudioContext | null = null;
let micStreaming = false;
let currentSpeaking = false;
function startAudioContext() {
  if (!audioContext) {
    audioContext = new AudioContext();
  }
}
function getAudioContext(): AudioContext {
  if (!audioContext) {
    throw new Error('AudioContext not initialized');
  }
  return audioContext;
}
/**
 *
 * @param {string} id
 * @param {(id: string, value:boolean)=> void} setSpeaking
 * @param {(id:string)=> void} onStop
 * @param {number} volume
 * @returns {SinkMetadata}
 */
function createAudioSink(id: string, volume: number, stereo: boolean, onSinkSpeaking: (playing: boolean) => void): WebAudioSink {
  let numberOfChannels = stereo ? 2 : 1;
  const audioContext = getAudioContext();
  const sink = new WebAudioSink(id, audioContext, numberOfChannels, (value) => {
    if (value) {
      cancelStopSpeaker();
    } else {
      debouncedStopSpeaker();
    }
  });
  console.debug("main: stream volume: " + volume);
  sink.setVolume(volume);
  // Sink teardown timeout id
  let speakerOffTimeout: any = null;
  console.debug(`main: starting sink ${id}`);
  audioContext.resume();
  function stopSpeaker() {
    console.debug(`main: stopping sink ${id}`);
    sink.close();
    activeSinks.delete(id);
    speakerOffTimeout = null;
  }
  function debouncedStopSpeaker() {
    if (!speakerOffTimeout) {
      speakerOffTimeout = setTimeout(() => stopSpeaker(), 1000);
    }
    console.log("CHECK NO SPEAKING")
    onSinkSpeaking(false);
  }
  function cancelStopSpeaker() {
    if (speakerOffTimeout) {
      clearTimeout(speakerOffTimeout);
      speakerOffTimeout = null;
    }
    console.log("CHECK SPEAKING")
    onSinkSpeaking(true);
  }
  return sink;
}

/**@type {Map<string, {audioCache: AudioCache, setVolume: (value:number) => void, speaking: boolean}>} */
const activeSinks = new Map<string, WebAudioSink>();

/**@type {Worker} */
let worker: Worker | null = null;

type WorkerActions = { setListening: (value: boolean) => void, setSpeaking: (value: boolean) => void, setOnline: (value: boolean) => void, setScreenSaverTime: (value: number) => void, };
/**
 *
 * @param {string} id
 * @param {string|null} token
 * @param {} actions
 * @returns {Promise<Worker>}
 */
export async function startWebsocketWorker(id: string, token: string, actions: WorkerActions) {
  let speakingCounter = 0;
  const defaultSinkConfig = {
    volume: 100,
    stereo: false,
  };
  let sinkConfig = { ...defaultSinkConfig };
  let remoteSpot = false;
  startAudioContext();
  const audioSource = new WebAudioSource(getAudioContext(), (buffers) => worker?.postMessage({ cmd: WorkerInCmd.LISTEN, buffers }));
  await audioSource.resume();
  // microphone stream checker
  setInterval(audioSource.resume.bind(audioSource), 15000);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      audioSource.resume();
    }
  });
  return new Promise((resolve, reject) => {
    try {
      worker = new Worker(new URL("./websocket-worker.js", import.meta.url), {
        name: "habspeaker-worker",
        type: "module",
      });
      worker.onmessage = (ev) => {
        if ((import.meta as any).env.DEV) {
          console.debug("worker => main thread:", ev.data);
        }
        switch (ev.data.cmd) {
          case WorkerOutCmd.CONFIGURE:
            // TODO: disallow configure after initialized
            const sinkVolume = ev.data.sinkVolume;
            if (sinkVolume != null) {
              sinkConfig.volume = sinkVolume;
            }
            const sinkStereo = ev.data.sinkStereo;
            if (sinkStereo != null) {
              sinkConfig.stereo = sinkStereo;
            }
            remoteSpot = !!ev.data.remoteSpot;
            if (ev.data.screenSaverTime != null && !isNaN(ev.data.screenSaverTime)) {
              actions.setScreenSaverTime(ev.data.screenSaverTime);
            }
            break;
          case WorkerOutCmd.INITIALIZED:
            actions.setOnline(true);
            if (remoteSpot) {
              console.debug("remote spot enabled, starting mic streaming");
              startMicStreaming();
            }
            break;
          case WorkerOutCmd.OFFLINE:
            sinkConfig = { ...defaultSinkConfig };
            remoteSpot = false;
            actions.setListening(false);
            actions.setOnline(false);
            stopMicStreaming();
            break;
          case WorkerOutCmd.SPEAK: {
            let sinkContext = activeSinks.get(ev.data.id);
            if (!sinkContext) {
              sinkContext = createAudioSink(ev.data.id, sinkConfig.volume, sinkConfig.stereo, onSinkSpeaking);
              activeSinks.set(ev.data.id, sinkContext);
            }
            sinkContext.playAudio(ev.data.buffer);
            break;
          }
          case WorkerOutCmd.START_LISTENING:
            actions.setListening(true);
            if (!remoteSpot) {
              startMicStreaming();
            }
            break;
          case WorkerOutCmd.STOP_LISTENING:
            actions.setListening(false);
            if (!remoteSpot) {
              stopMicStreaming();
            }
            break;
          case WorkerOutCmd.SINK_VOLUME:
            sinkConfig.volume = ev.data.value;
            activeSinks.forEach(sink => sink.setVolume(sinkConfig.volume));
            break;
        }
      };
      worker.onerror = (err) => {
        console.error(err);
        reject(err);
      };
      worker.postMessage({
        cmd: WorkerInCmd.INITIALIZE,
        id,
        token,
        sampleRate: getAudioContext().sampleRate,
      });
      resolve(worker);
    } catch (error) {
      reject(error);
    }
  });
  function startMicStreaming() {
    if (!micStreaming) {
      console.debug("starting microphone audio streaming");
      audioSource.start().catch((err) => console.error(err));
      micStreaming = true;
    }
  }
  function stopMicStreaming() {
    if (micStreaming) {
      console.debug("stopping microphone audio streaming");
      try {
        audioSource.stop();
      } catch (ignored) { }
      micStreaming = false;
    }
  }
  function onSinkSpeaking(speaking: boolean) {
    const speakingValue = Array.from(activeSinks.values()).some(i => i.isPlaying());
    if (speakingValue != currentSpeaking) {
      currentSpeaking = speakingValue;
      console.log("SPEAKING: " + speakingValue)
      actions.setSpeaking(speakingValue);
    }
  }
}
