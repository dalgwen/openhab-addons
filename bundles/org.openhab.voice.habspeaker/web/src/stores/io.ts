import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { useScreenSaverStore } from "./screen-saver";
import { useAuthStore } from "./auth";
import { PlaybackState, useMediaSessionStore } from "./media-players/media-session";
import { useSpotifyPlayerStore } from "./media-players/spotify-player";
import { WebAudioSource } from "../utils/web-source";
import { WebAudioSink } from "../utils/web-sink";
import { MediaStateCmd, WorkerInCmd, WorkerOutCmd, WorkerOutCmdType } from "../utils/io-types";
import { getUrlOpenHAB } from "../platforms";
export const useIOStore = defineStore("io", () => {
  let audioContext: AudioContext | null = null;
  let audioSource: WebAudioSource | null = null;
  let worker: Worker | null = null;
  let micStreaming = false;
  let currentSpeaking = false;
  const activeSinks = new Map<string, WebAudioSink>();
  let webSocketProcessorNode: AudioNode;
  let localKsProcessorNode: AudioNode | null = null;
  let stopLocalKsProcessorNode: (() => void) | null = null;
  let speakerLabel = ref("HAB Speaker");
  const authStore = useAuthStore();
  const spotifyStore = useSpotifyPlayerStore();
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController } = storeToRefs(mediaSessionStore);
  const { awakeScreenSaver, setScreenSaverTime } = useScreenSaverStore();
  // state
  const listening = ref(false);
  const speaking = ref(false);
  const online = ref(false);
  // voice setup
  function startVoiceAudioContext() {
    if (!audioContext) {
      // initialize to 16000 to avoid resampling, some browsers can ignore this
      const voiceSampleRate = 16000;
      var isChromium = !!(window as any).chrome;
      let options: AudioContextOptions = {};
      if (isChromium) {
        options.sampleRate = voiceSampleRate;
      }
      audioContext = new AudioContext(options);
      console.debug("Audio resample is needed: " + (audioContext.sampleRate !== voiceSampleRate));
    }
  }
  function getVoiceAudioContext(): AudioContext {
    if (!audioContext) {
      throw new Error('AudioContext not initialized');
    }
    return audioContext;
  }
  /**
   * Returns a processor node that sends data through the websocket 
  */
  function getWSProcessorNode() {
    if (webSocketProcessorNode) {
      return webSocketProcessorNode;
    }
    const audioContext = getVoiceAudioContext();
    const _webSocketProcessorNode = audioContext.createScriptProcessor(4096, 1, 1);
    _webSocketProcessorNode.onaudioprocess = ({ inputBuffer }: AudioProcessingEvent) => {
      const buffers: Float32Array[] = [];
      for (let i = 0; i < inputBuffer.numberOfChannels; i++) {
        buffers[i] = inputBuffer.getChannelData(i);
      }
      worker?.postMessage({ cmd: WorkerInCmd.LISTEN, buffers });
    }
    return webSocketProcessorNode = _webSocketProcessorNode;
  }
  /**
   * Returns a processor node that spots for the keyword
   */
  async function initLocalKsProcessor(keyword: string, options: { averagedThreshold?: number, threshold?: number, eagerMode?: boolean, }) {
    console.debug("main: starting local keyword spotting using rustpotter");
    const { RustpotterService } = await import("rustpotter-worklet");
    const wasmModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter_wasm_bg.wasm', import.meta.url);
    const workletModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotterWorklet.js', import.meta.url);
    const rp = new RustpotterService({
      workletPath: workletModuleUrl.href,
      wasmPath: wasmModuleUrl.href,
      averagedThreshold: options.averagedThreshold ?? 0.2,
      threshold: options.threshold ?? 0.5,
      eagerMode: options.eagerMode ?? true,
    });
    rp.onspot = (name, score) => {
      console.debug(`main: spotted '${name}' with score: ${score}`);
      sendSpot();
    };
    if (stopLocalKsProcessorNode) {
      stopLocalKsProcessorNode();
    }
    stopLocalKsProcessorNode = async () => {
      console.debug("main: stopping local keyword spotting");
      try {
        await rp.close();
      } catch (error) {
        console.warn(error)
      }
    };
    const node = await rp.getProcessorNode(getVoiceAudioContext());
    let headers: HeadersInit = {};
    const accessToken = authStore.getAccessToken();
    if (accessToken.length) {
      headers["Authorization"] = `Bearer ${accessToken}`;
    }
    await rp.addWakewordByPath(`${await getUrlOpenHAB()}/rest/habspeaker/rustpotter/${keyword.replaceAll(" ", "_")}`, headers);
    try {
      node.disconnect();
    } catch (ignored) {
    }
    return node;
  }
  // worker actions
  function setListening(value: boolean) {
    awakeScreenSaver();
    mediaSessionStore.muteMediaVolume(value);
    listening.value = value;
  }
  function setSpeaking(value: boolean) {
    awakeScreenSaver();
    mediaSessionStore.muteMediaVolume(value);
    speaking.value = value;
  }
  function setOnline(value: boolean) {
    awakeScreenSaver();
    online.value = value;
    spotifyStore.isEnabled().then(spotifyEnabled => {
      if (spotifyEnabled) {
        if (value) {
          spotifyStore.initPlayer(speakerLabel.value)
            .then(() => spotifyStore.connect())
            .then((connected) => console.debug("Spotify is connected: " + connected))
            .catch((err) => console.error("Error connecting to spotify: ", err));
        } else if (!value) {
          spotifyStore.disconnect()
            .then(() => console.debug("Spotify is disconnected"))
            .catch(() => console.error("Error disconnecting from spotify"));
        }
      }
    });
    if (!value) {
      mediaSessionStore.stopMedia();
    }
  }
  function updateSpotifyToken(token: string) {
    spotifyStore.updateToken(token);
  }
  // worker setup
  function postToWorker(cmd: string, args: { [key: string]: any } = {}) {
    try {
      if (worker) {
        worker.postMessage({ cmd, ...args });
      } else {
        console.error("Worker not running");
      }
    } catch (error) {
      console.error("Unable to post to worker", error);
    }
  }
  // io exposed actions
  function sendSpot() {
    if (online.value) {
      postToWorker(WorkerInCmd.ON_SPOT);
    }
  }
  function resetConnection(id: string) {
    postToWorker(WorkerInCmd.RESET_CONNECTION, { id });
  }
  function sendMediaState(state: MediaStateCmd) {
    if (online.value) {
      postToWorker(WorkerInCmd.MEDIA_STATE, state);
    }
  }
  function setAuthToken(token: string) {
    if (worker) {
      postToWorker(WorkerInCmd.TOKEN_RENEW, { token });
    }
  }
  async function init(id: string, token: string) {
    const defaultSinkConfig = {
      volume: 100,
    };
    let sinkConfig = { ...defaultSinkConfig };
    let remoteSpotMode = false;
    startVoiceAudioContext();
    audioSource = new WebAudioSource(getVoiceAudioContext());
    await audioSource.resume();
    // microphone stream checker, to keep the stream alive on undetected disconnections  
    setInterval(audioSource.resume.bind(audioSource), 10000);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) {
        audioSource?.resume();
      }
    });
    return new Promise((resolve, reject) => {
      try {
        worker = new Worker(new URL("../utils/io-worker.ts", import.meta.url), {
          name: "hab_speaker-worker",
          type: "module",
        });
        worker.onmessage = (ev) => {
          if ((import.meta as any).env.DEV) {
            console.debug("worker => main thread:", ev.data);
          }
          const command = ev.data.cmd as WorkerOutCmd;
          switch (command) {
            case WorkerOutCmd.CONFIGURE:
              // TODO: disallow configure after initialized
              const { sinkVolume, spotMode, screenSaverTime, spotifyToken, label, spotConfig, } = ev.data as WorkerOutCmdType<typeof command>;
              if (label) {
                speakerLabel.value = label;
              }
              if (sinkVolume != null) {
                sinkConfig.volume = sinkVolume;
              }
              remoteSpotMode = false;
              switch (spotMode) {
                case "server":
                  remoteSpotMode = true;
                  break;
                case "rustpotter_web":
                  if (spotConfig?.keyword) {
                    initLocalKsProcessor(spotConfig?.keyword, { ...spotConfig })
                      .then((audioProcessor) => {
                        localKsProcessorNode = audioProcessor;
                        audioSource?.start(localKsProcessorNode).catch((err) => console.error(err));
                      })
                      .catch(err => console.error(err));
                  } else {
                    console.warn("Missed spotConfig configuration");
                  }
                  break;
                case "none":
                default:
                  break;
              }
              if (screenSaverTime != null && !isNaN(screenSaverTime)) {
                setScreenSaverTime(screenSaverTime);
              }
              if (spotifyToken) {
                updateSpotifyToken(spotifyToken);
              }
              break;
            case WorkerOutCmd.INITIALIZED:
              setOnline(true);
              if (remoteSpotMode) {
                console.debug("remote spot enabled, starting mic streaming");
                startMicStreaming();
              }
              break;
            case WorkerOutCmd.OFFLINE:
              sinkConfig = { ...defaultSinkConfig };
              remoteSpotMode = false;
              setListening(false);
              setOnline(false);
              stopAllMicProcessors();
              break;
            case WorkerOutCmd.SPEAK: {
              const speakData = ev.data as WorkerOutCmdType<typeof command>;
              let sinkContext = activeSinks.get(speakData.id);
              if (!sinkContext) {
                sinkContext = createAudioSink(speakData.id, sinkConfig.volume, speakData.channels, onSinkSpeaking);
                activeSinks.set(sinkContext.getId(), sinkContext);
              }
              sinkContext.playAudio(speakData.buffer);
              break;
            }
            case WorkerOutCmd.START_LISTENING:
              setListening(true);
              if (!remoteSpotMode) {
                startMicStreaming();
              }
              break;
            case WorkerOutCmd.STOP_LISTENING:
              setListening(false);
              if (!remoteSpotMode) {
                stopMicStreaming();
              }
              break;
            case WorkerOutCmd.SINK_VOLUME:
              const { value } = ev.data as WorkerOutCmdType<typeof command>;
              sinkConfig.volume = value;
              activeSinks.forEach(sink => sink.setVolume(sinkConfig.volume));
              break;
            case WorkerOutCmd.MEDIA_COMMAND:
              const mediaCommandData = ev.data as WorkerOutCmdType<typeof command>;
              if ('start' === mediaCommandData.type) {
                mediaSessionStore.startMedia(mediaCommandData.provider, {
                  mediaId: mediaCommandData.mediaId,
                  playlistId: mediaCommandData.playlistId,
                  playlistIndex: mediaCommandData.playlistIndex,
                  startSecond: mediaCommandData.second,
                });
                return;
              }
              const mediaSessionCtrl = mediaController.value;
              if (!mediaSessionCtrl) {
                console.warn("Media is not started");
                return;
              }
              switch (mediaCommandData.type) {
                case 'play':
                  mediaSessionCtrl.play();
                  break;
                case 'pause':
                  mediaSessionCtrl.pause();
                  break;
                case 'stop':
                  mediaSessionCtrl.getPlaybackState().then((state) => {
                    if (state === PlaybackState.PLAYING) {
                      mediaSessionCtrl.stop();
                    }
                    mediaSessionStore.stopMedia();
                  });
                  break;
                case 'next':
                  mediaSessionCtrl.next();
                  break;
                case 'previous':
                  mediaSessionCtrl.previous();
                  break;
                case 'seek':
                  mediaSessionCtrl.seek(mediaCommandData.second);
                  break;
                case 'volume':
                  mediaSessionStore.setMediaVolume(mediaCommandData.level);
                  break;
                default:
                  console.error("Unsupported media command: ", mediaCommandData);
              }
              break;
            case WorkerOutCmd.SPOTIFY_TOKEN:
              const spotifyTokenData = ev.data as WorkerOutCmdType<typeof command>;
              spotifyStore.updateToken(spotifyTokenData.token);
              break;
          }
        };
        worker.onerror = (err) => {
          console.error(err);
          reject(err);
        };
        getUrlOpenHAB().then((ohUrl) => {
          worker?.postMessage({
            cmd: WorkerInCmd.INITIALIZE,
            id,
            token,
            sampleRate: getVoiceAudioContext().sampleRate,
            ohUrl,
          });
          resolve(worker);
        }).catch(reject);
      } catch (error) {
        reject(error);
      }
    });
    function startMicStreaming() {
      if (!micStreaming) {
        console.debug("starting microphone audio streaming");
        const processors: AudioNode[] = [getWSProcessorNode()];
        if (localKsProcessorNode) {
          processors.unshift(localKsProcessorNode)
        }
        audioSource?.start(...processors).catch((err) => console.error(err));
        micStreaming = true;
      }
    }
    function stopMicStreaming() {
      if (micStreaming) {
        console.debug("stopping microphone audio streaming");
        const processors: AudioNode[] = [];
        if (localKsProcessorNode) {
          processors.unshift(localKsProcessorNode);
        }
        if (processors.length > 0) {
          audioSource?.start(...processors).catch((err) => console.error(err));
        } else {
          audioSource?.stop();
        }
        micStreaming = false;
      }
    }
    function stopAllMicProcessors() {
      audioSource?.stop();
      if (localKsProcessorNode) {
        localKsProcessorNode = null;
        if (stopLocalKsProcessorNode) {
          stopLocalKsProcessorNode();
          stopLocalKsProcessorNode = null;
        }
      }
    }
    function onSinkSpeaking(speaking: boolean) {
      const speakingValue = Array.from(activeSinks.values()).some(i => i.isPlaying());
      if (speakingValue != currentSpeaking) {
        currentSpeaking = speakingValue;
        setSpeaking(speakingValue);
      }
    }
  }

  /**
   *
   */
  function createAudioSink(id: string, volume: number, channels: number, onSinkSpeaking: (playing: boolean) => void): WebAudioSink {
    const audioContext = getVoiceAudioContext();
    const sink = new WebAudioSink(id, audioContext, channels, (value) => {
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
      onSinkSpeaking(false);
    }
    function cancelStopSpeaker() {
      if (speakerOffTimeout) {
        clearTimeout(speakerOffTimeout);
        speakerOffTimeout = null;
      }
      onSinkSpeaking(true);
    }
    return sink;
  }
  return {
    listening,
    online,
    speaking,
    init,
    sendSpot,
    resetConnection,
    sendMediaState,
    setAuthToken,
  };
});
