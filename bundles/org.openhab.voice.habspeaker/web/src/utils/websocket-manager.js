import { WorkerInCmd, WorkerOutCmd } from "./websocket-worker";
/**@type {AudioContext} */
let audioContext = null;

// Audio Record

/**@type {ScriptProcessorNode} */
let processorNode = null;
/**@type {MediaStream} */
let stream = null;
/**@type {MediaStreamAudioSourceNode} */
let sourceNode = null;
async function setupAudio() {
  if (!audioContext) {
    audioContext = new AudioContext();
  }
  if (!stream) {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: true,
      video: false,
    });
  }
  if (!sourceNode) {
    sourceNode = audioContext.createMediaStreamSource(stream);
  }
}

class AudioCache {
  silence = new Float32Array(4096);
  buffer = new Float32Array(0);
  readAudioData(n) {
    var segment = this.buffer.subarray(0, n);
    this.buffer = this.buffer.subarray(n, this.buffer.length);
    return segment;
  }
  writeAudioData(buffer) {
    var currentCacheLength = this.buffer.length;
    var newBuffer = new Float32Array(currentCacheLength + buffer.length);
    newBuffer.set(this.buffer, 0);
    newBuffer.set(buffer, currentCacheLength);
    this.buffer = newBuffer;
  }
  available() {
    return !!this.buffer.length;
  }
  reset() {
    this.buffer = new Float32Array(0);
  }
}
/**
 *
 * @param {string} id
 * @param {(id: string, value:boolean)=> void} setSpeaking
 * @param {(id:string)=> void} onStop
 * @returns {AudioCache}
 */
function setupSinkAudio(id, setSpeaking, onStop) {
  /**@type {ScriptProcessorNode} */
  let sinkProcessorNode = null;
  /**@type {MediaStreamAudioSourceNode} */
  let gainNode = null;
  const audioCache = new AudioCache();
  // Sink teardown timeout id
  let speakerOffTimeout = null;
  console.debug(`main: starting sink ${id}`);
  audioContext.resume();
  function stopSpeaker() {
    console.debug(`main: stopping sink ${id}`);
    gainNode.disconnect();
    sinkProcessorNode.disconnect();
    onStop(id);
  }
  function debouncedStopSpeaker() {
    if (!speakerOffTimeout) {
      speakerOffTimeout = setTimeout(() => stopSpeaker(), 3000);
    }
  }
  function cancelStopSpeaker() {
    if (speakerOffTimeout) {
      clearTimeout(speakerOffTimeout);
      speakerOffTimeout = null;
    }
  }
  sinkProcessorNode = audioContext.createScriptProcessor(4096, 0, 1);
  sinkProcessorNode.onaudioprocess = function (e) {
    if (audioCache.available()) {
      setSpeaking(id, true);
      cancelStopSpeaker();
      e.outputBuffer
        .getChannelData(0)
        .set(audioCache.readAudioData(e.outputBuffer.length));
    } else {
      setSpeaking(id, false);
      debouncedStopSpeaker();
      e.outputBuffer.getChannelData(0).set(audioCache.silence);
    }
  };
  gainNode = audioContext.createGain();
  sinkProcessorNode.connect(gainNode);
  audioCache.reset();
  gainNode.connect(audioContext.destination);
  return audioCache;
}

/**@type {Map<string, {audioCache: AudioCache, speaking: boolean}>} */
const activeSinks = new Map();
/**
 * 
 * @param {string} id 
 */
function onSinkStop(id) {
  activeSinks.delete(id);
}

/**@type {Worker} */
let worker = null;
/**
 *
 * @param {string} id
 * @param {string} label
 * @param {string} listeningItem
 * @param {string|null} token
 * @param {{setListening:(value:boolean)=> void, setSpeaking:(value:boolean)=> void, setOnline:(value:boolean)=>void }} actions
 * @returns {Promise<Worker>}
 */
export async function startWebsocketWorker(id, label, listeningItem, token, actions) {
  await setupAudio();
  function onSinkSpeaking(id, speaking) {
    const sinkContext = activeSinks.get(id);
    if (sinkContext) {
      sinkContext.speaking = speaking;
    }
    actions.setSpeaking(Array.from(activeSinks.values()).some(i => i.speaking));
  }
  return new Promise((resolve, reject) => {
    try {
      processorNode = audioContext.createScriptProcessor(4096, 1, 1);
      processorNode.onaudioprocess = ({ inputBuffer }) => {
        const buffers = [];
        for (let i = 0; i < inputBuffer.numberOfChannels; i++) {
          buffers[i] = inputBuffer.getChannelData(i);
        }
        worker.postMessage({ cmd: WorkerInCmd.LISTEN, buffers });
      };
      sourceNode.connect(processorNode);
      worker = new Worker(new URL("./websocket-worker.js", import.meta.url), {
        name: "habspeaker-worker",
        type: "module",
      });
      worker.onmessage = (ev) => {
        console.debug("worker => main thread:", ev.data);
        switch (ev.data.cmd) {
          case WorkerOutCmd.INITIALIZED:
            actions.setOnline(true);
            break;
          case WorkerOutCmd.OFFLINE:
            actions.setOnline(false);
            actions.setListening(false);
            try {
              processorNode.disconnect(audioContext.destination);
            } catch (error) {
              // ignore this error
            }
            break;
          case WorkerOutCmd.SPEAK: {
            let sinkContext = activeSinks.get(ev.data.id);
            if (!sinkContext) {
              sinkContext = { audioCache: setupSinkAudio(ev.data.id, onSinkSpeaking, onSinkStop), speaking: false };
              activeSinks.set(ev.data.id, sinkContext);
            }
            sinkContext.audioCache.writeAudioData(ev.data.buffer);
            break;
          }
          case WorkerOutCmd.START_LISTENING:
            actions.setListening(true);
            audioContext
              .resume()
              .then(() => setupAudio())
              .then(() => {
                processorNode.connect(audioContext.destination);
              })
              .catch((err) => console.error(err));
            break;
          case WorkerOutCmd.STOP_LISTENING:
            actions.setListening(false);
            try {
              processorNode.disconnect(audioContext.destination);
            } catch (ignored) {
              // ignore the error
            }
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
        label,
        token,
        listeningItem,
        sampleRate: audioContext.sampleRate,
      });
      resolve(worker);
    } catch (error) {
      reject(error);
    }
  });
}
