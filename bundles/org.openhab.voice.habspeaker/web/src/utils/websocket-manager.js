import { WorkerInCmd, WorkerOutCmd } from "./websocket-worker";
// detect IOS
function iOS() {
  return [
    'iPad Simulator',
    'iPhone Simulator',
    'iPod Simulator',
    'iPad',
    'iPhone',
    'iPod'
  ].includes(navigator.platform)
    // iPad on iOS 13 detection
    || (navigator.userAgent.includes("Mac") && "ontouchend" in document)
}
const isIOS = iOS();
/**@type {AudioContext} */
let audioContext = null;
let micStreaming = false;
const BUFFER_SIZE = 2048;
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
      audio: {
        autoGainControl: true,
        echoCancellation: true,
        noiseSuppression: true,
        suppressLocalAudioPlayback: false,
      },
      video: false,
    });
  }
  if (!sourceNode) {
    sourceNode = audioContext.createMediaStreamSource(stream);
  }
}

class AudioCache {
  silence = new Float32Array(BUFFER_SIZE);
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
 * @typedef {Object} SinkMetadata
 * @property {number} audioCache - The audio cache
 * @property {(value:number)=>void} setVolume - Volume
 */
/**
 *
 * @param {string} id
 * @param {(id: string, value:boolean)=> void} setSpeaking
 * @param {(id:string)=> void} onStop
 * @param {number} volume
 * @returns {SinkMetadata}
 */
function setupSinkAudio(id, setSpeaking, onStop, volume, stereo) {
  let numberOfChannels = stereo ? 2 : 1;
  /**@type {ScriptProcessorNode} */
  let sinkProcessorNode = null;
  /**@type {GainNode} */
  let gainNode = null;
  const audioCache = new AudioCache();
  // Sink teardown timeout id
  let speakerOffTimeout = null;
  console.debug(`main: starting sink ${id}`);
  audioContext.resume();
  var audioElement = document.createElement('audio');
  function stopSpeaker() {
    console.debug(`main: stopping sink ${id}`);
    gainNode.disconnect();
    sinkProcessorNode.disconnect();
    onStop(id);
    audioElement.remove();
  }
  function debouncedStopSpeaker() {
    if (!speakerOffTimeout) {
      speakerOffTimeout = setTimeout(() => stopSpeaker(), 1000);
    }
  }
  function cancelStopSpeaker() {
    if (speakerOffTimeout) {
      clearTimeout(speakerOffTimeout);
      speakerOffTimeout = null;
    }
  }
  sinkProcessorNode = audioContext.createScriptProcessor(BUFFER_SIZE, 0, numberOfChannels);
  sinkProcessorNode.onaudioprocess = function (e) {
    if (audioCache.available()) {
      setSpeaking(id, true);
      cancelStopSpeaker();
      var audioData = audioCache.readAudioData(e.outputBuffer.length * numberOfChannels);
      var channelsData = [];
      if (numberOfChannels == 1) {
        channelsData[0] = audioData;
      } else {
        var length = audioData.byteLength / audioData.BYTES_PER_ELEMENT;
        for (let s = 0; s < length; s++) {
          // the channel index
          const c = s % numberOfChannels;
          // the index inside the buffer channel
          const i = (s - c) / numberOfChannels;
          const channelData = channelsData[c] = (channelsData[c] ?? new Float32Array(length / numberOfChannels));
          channelData[i] = audioData[s];
        }
      }
      for (let c = 0; c < numberOfChannels; c++) {
        e.outputBuffer
          .getChannelData(c)
          .set(channelsData[0]);
      }
    } else {
      setSpeaking(id, false);
      debouncedStopSpeaker();
      for (let c = 0; c < numberOfChannels; c++) {
        e.outputBuffer.getChannelData(c).set(audioCache.silence);
      }
    }
  };
  gainNode = audioContext.createGain();
  const setVolume = (value) => gainNode.gain.setValueAtTime((value / 100) * isIOS ? 10 : 1, audioContext.currentTime);
  console.debug("main: stream volume: " + volume);
  setVolume(volume);
  sinkProcessorNode.connect(gainNode);
  audioCache.reset();
  var destination = audioContext.createMediaStreamDestination();
  gainNode.connect(destination);
  audioElement.srcObject = destination.stream;
  audioElement.autoplay = true;
  return { audioCache, setVolume };
}

/**@type {Map<string, {audioCache: AudioCache, setVolume: (value:number) => void, speaking: boolean}>} */
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
 * @param {string|null} token
 * @param {{setListening:(value:boolean)=> void, setSpeaking:(value:boolean)=> void, setOnline:(value:boolean)=>void }} actions
 * @returns {Promise<Worker>}
 */
export async function startWebsocketWorker(id, token, actions) {
  const defaultSinkConfig = {
    volume: 100,
    stereo: false,
  };
  let sinkConfig = { ...defaultSinkConfig };
  let remoteSpot = false;
  await setupAudio();
  let currentSpeaking = false;
  function onSinkSpeaking(id, speaking) {
    const sinkContext = activeSinks.get(id);
    if (sinkContext) {
      sinkContext.speaking = speaking;
    }
    const speakingValue = Array.from(activeSinks.values()).some(i => i.speaking);
    if (speakingValue != currentSpeaking) {
      currentSpeaking = speakingValue;
      actions.setSpeaking(speakingValue);
    }
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
      // Add a gain node to the microphone to reduce noise
      var micGainNode = audioContext.createGain();
      micGainNode.gain.value = 0.5;
      micGainNode.connect(processorNode);
      sourceNode.connect(micGainNode);
      worker = new Worker(new URL("./websocket-worker.js", import.meta.url), {
        name: "habspeaker-worker",
        type: "module",
      });
      worker.onmessage = (ev) => {
        if (import.meta.env.DEV) {
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
              sinkContext = { ...setupSinkAudio(ev.data.id, onSinkSpeaking, onSinkStop, sinkConfig.volume, sinkConfig.stereo), speaking: false };
              activeSinks.set(ev.data.id, sinkContext);
            }
            sinkContext.audioCache.writeAudioData(ev.data.buffer);
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
            activeSinks.forEach(sinkContext => {
              sinkContext.setVolume(sinkConfig.volume);
            });
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
        sampleRate: audioContext.sampleRate,
      });
      resolve(worker);
    } catch (error) {
      reject(error);
    }
  });
  function startMicStreaming() {
    if (!micStreaming) {
      console.debug("starting microphone audio streaming");
      audioContext
        .resume()
        .then(() => setupAudio())
        .then(() => {
          processorNode.connect(audioContext.destination);
        })
        .catch((err) => console.error(err));
      micStreaming = true;
    }
  }

  function stopMicStreaming() {
    if (micStreaming) {
      console.debug("stopping microphone audio streaming");
      try {
        processorNode.disconnect(audioContext.destination);
      } catch (ignored) { }
      micStreaming = false;
    }
  }
}
