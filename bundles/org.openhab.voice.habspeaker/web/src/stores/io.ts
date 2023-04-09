import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { ReentrantLock } from "reentrant-lock";
import { useScreenSaverStore } from "./screen-saver";
import { useAuthStore } from "./auth";
import { PlaybackState, useMediaSessionStore } from "./media-players/media-session";
import { useSpotifyPlayerStore } from "./media-players/spotify-player";
import { WebAudioSource } from "../utils/web-source";
import { WebAudioSink } from "../utils/web-sink";
import { WebAudioSink as WebAudioSinkDeprecated } from "../utils/web-sink-deprecated";
import { MediaStateCmd, RustpotterOptions, WorkerInCmd, WorkerOutCmd, WorkerOutCmdType } from "../utils/io-types";
import audioPortWorklet from "../utils/web-source-worklet.ts?sharedworker&url";
import { useSettingsStore } from "./settings";
import { MessageACKManager } from "../utils/message-ack-manager";
import { platform } from "../platforms";

export const useIOStore = defineStore("io", () => {
  const messageACKs = new MessageACKManager("main");
  const mediaCommandLock = new ReentrantLock();
  let listenPortACK: number | undefined;
  let audioContext: AudioContext | null = null;
  let audioSource: WebAudioSource | null = null;
  let worker: Worker | null = null;
  let micStreaming = false;
  let currentSpeaking = false;
  const activeSinks = new Map<string, WebAudioSink>();
  let localKsProcessorNode: AudioNode | null = null;
  let stopLocalKsProcessorNode: (() => void) | null = null;
  let speakerLabel = ref("HAB Speaker");
  const authStore = useAuthStore();
  const { getOHUrl } = useSettingsStore();
  const spotifyStore = useSpotifyPlayerStore();
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController } = storeToRefs(mediaSessionStore);
  const { awakeScreenSaver, setScreenSaverTime, enableScreenDim } = useScreenSaverStore();
  // state
  const listening = ref(false);
  const speaking = ref(false);
  const online = ref(false);
  // detect browser
  let _isIOSBrowser: boolean | undefined = undefined;
  function isIOSBrowser() {
    if (_isIOSBrowser != null) {
      return _isIOSBrowser;
    }
    return _isIOSBrowser = [
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
  function isChromeBasedBrowser() {
    return !!(window as any).chrome;
  }
  // voice setup
  function startVoiceAudioContext() {
    if (!audioContext) {
      // this is the sample rate required by the server,
      // by setting audio context to use it some resampling
      // on the io webworker will be avoided.
      const voiceSampleRate = 16000;
      let options: AudioContextOptions = {};
      if (isChromeBasedBrowser()) {
        // built-in resample seems to work great in chrome, not in safari, firefox remains untested.
        options.sampleRate = voiceSampleRate;
      }
      audioContext = new AudioContext(options);
      console.debug("Audio resample is needed: " + (audioContext.sampleRate !== voiceSampleRate));
    }
  }
  function isWorkletSupported() {
    const audioContext = getVoiceAudioContext();
    return !!audioContext.audioWorklet;
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
  async function getWSProcessorNode() {
    const audioContext = getVoiceAudioContext();
    if (isWorkletSupported()) {
      const _webSocketWorkletNode = new AudioWorkletNode(audioContext, 'habspeaker-source-worklet', { numberOfInputs: 1, numberOfOutputs: 0, channelCount: 1, channelCountMode: 'explicit' });
      listenPortACK = messageACKs.createACK();
      const command = { cmd: WorkerInCmd.LISTEN_PORT, port: _webSocketWorkletNode.port, ack: listenPortACK };
      worker?.postMessage(command, [command.port]);
      await messageACKs.awaitACK(listenPortACK);
      listenPortACK = undefined;
      return _webSocketWorkletNode as AudioNode;
    } else {
      const audioMessagePort = new MessageChannel();
      listenPortACK = messageACKs.createACK();
      const command = { cmd: WorkerInCmd.LISTEN_PORT, port: audioMessagePort.port1, ack: listenPortACK };
      worker?.postMessage(command, [command.port]);
      await messageACKs.awaitACK(listenPortACK);
      listenPortACK = undefined;
      const _webSocketProcessorNode = audioContext.createScriptProcessor(4096, 1, 1);
      _webSocketProcessorNode.onaudioprocess = ({ inputBuffer }: AudioProcessingEvent) => {
        const buffers: Float32Array[] = [];
        for (let i = 0; i < inputBuffer.numberOfChannels; i++) {
          buffers[i] = inputBuffer.getChannelData(i);
        }
        audioMessagePort.port2.postMessage(buffers, [buffers[0].buffer]);
      }
      return _webSocketProcessorNode;
    }
  }
  /**
   * Returns a processor node that spots for the keyword
   */
  async function initLocalRustpotterProcessor(keyword: string, options: RustpotterOptions) {
    console.debug("main: starting local keyword spotting using rustpotter");
    const { RustpotterService, ScoreMode } = await import("rustpotter-worklet");
    const wasmModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter_wasm_bg.wasm', import.meta.url);
    const workletModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter-worklet.js', import.meta.url);
    const scoreMode = (ScoreMode[options.scoreMode as any] as any) ?? ScoreMode.max;
    const rp = new RustpotterService({
      workletPath: workletModuleUrl.href,
      wasmPath: wasmModuleUrl.href,
      averagedThreshold: options.averagedThreshold,
      threshold: options.threshold,
      minScores: options.minScores,
      scoreMode: scoreMode,
      minGain: options.minGain,
      maxGain: options.maxGain,
      bandPassEnabled: options.bandPassEnabled,
      bandPassLowCutoff: options.bandPassLowCutoff,
      bandPassHighCutoff: options.bandPassHighCutoff,
      comparatorBandSize: options.comparatorBandSize,
      comparatorRef: options.comparatorRef,
      gainNormalizerEnabled: options.gainNormalizerEnabled,
      gainRef: options.gainRef,
    });
    rp.onspot = (detection) => {
      console.debug(`main: spotted '${detection.name}' with score: ${detection.score}`);
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
    await rp.addWakewordByPath(`${await getOHUrl()}/rest/habspeaker/rustpotter/${keyword.replaceAll(" ", "_")}`, headers);
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
    if (!value) {
      if (listenPortACK) {
        messageACKs.abortACK(listenPortACK);
        listenPortACK = undefined;
      }
      mediaSessionStore.stopMedia();
    }
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
    if (online.value && audioSource && !audioSource.isSuspended()) {
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
    if (isWorkletSupported()) {
      await WebAudioSink.registerProcessor(getVoiceAudioContext());
      await getVoiceAudioContext().audioWorklet.addModule(audioPortWorklet);
    }
    // microphone stream checker, to keep the stream alive on undetected disconnections  
    setInterval(() => {
      if (audioSource && !audioSource.isSuspended()) {
        audioSource.resume();
      }
    }, 10000);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && audioSource && !audioSource.isSuspended()) {
        audioSource.resume();
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
              (async () => {
                const { sinkVolume, spotMode, screenSaverTime, spotifyToken, label, dimScreen, keepAwake, spotConfig, ack } = ev.data as WorkerOutCmdType<typeof command>;
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
                      try {
                        const ksAudioProcessor = await initLocalRustpotterProcessor(spotConfig?.keyword, { ...spotConfig });
                        localKsProcessorNode = ksAudioProcessor;
                        await audioSource?.start(localKsProcessorNode);
                      } catch (error) {
                        console.error("Unable to start local ks processor", error);
                      }
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
                enableScreenDim(!!dimScreen);
                await platform.keepDeviceAwake(!!keepAwake);
                if (spotifyToken) {
                  updateSpotifyToken(spotifyToken);
                }
                postToWorker(WorkerInCmd.ACK_MESSAGE, { code: ack });
              })();
              break;
            case WorkerOutCmd.ACK_MESSAGE:
              const ackData = ev.data as WorkerOutCmdType<typeof command>;
              messageACKs.confirmACK(ackData.code);
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
            case WorkerOutCmd.SPEAK_PORT: {
              const speakData = ev.data as WorkerOutCmdType<typeof command>;
              // create a message channel to communicate the worker with the sink
              const audioMessageChannel = new MessageChannel();
              const sink = createAudioSink(speakData.id, sinkConfig.volume, speakData.channels, audioMessageChannel.port1, onSinkSpeaking);
              const speakPortCmd = { cmd: WorkerInCmd.SPEAK_PORT, id: sink.getId(), port: audioMessageChannel.port2 };
              // transfer port to worker
              worker?.postMessage(speakPortCmd, [speakPortCmd.port]);
              break;
            }
            case WorkerOutCmd.START_LISTENING:
              if (!online.value) {
                console.debug("main: ignoring start listening message before init");
                return;
              }
              setListening(true);
              if (!remoteSpotMode) {
                startMicStreaming();
              }
              break;
            case WorkerOutCmd.STOP_LISTENING:
              if (!online.value) {
                console.debug("main: ignoring stop listening message before init");
                return;
              }
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
              runMediaCommand(mediaCommandData).catch(err => console.error(err));
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
        getOHUrl().then((ohUrl) => {
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
    async function startMicStreaming() {
      if (!micStreaming) {
        micStreaming = true;
        console.debug("starting microphone audio streaming");
        const processors: AudioNode[] = [await getWSProcessorNode()];
        if (!micStreaming) {
          console.warn("main: start microphone audio aborted");
          return;
        }
        if (localKsProcessorNode) {
          processors.unshift(localKsProcessorNode)
        }
        audioSource?.start(...processors).catch((err) => console.error(err));
      } else {
        console.warn("main: trying to start microphone streaming but it's already started!");
      }
    }
    function stopMicStreaming() {
      if (micStreaming) {
        console.debug("stopping microphone audio streaming");
        micStreaming = false;
        const processors: AudioNode[] = localKsProcessorNode ? [localKsProcessorNode] : [];
        if (processors.length > 0) {
          audioSource?.start(...processors).catch((err) => console.error(err));
        } else {
          audioSource?.stop();
        }
      } else {
        console.warn("main: trying to stop microphone streaming but it's already stopped!");
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
  async function runMediaCommand(mediaCommandData: WorkerOutCmdType<WorkerOutCmd.MEDIA_COMMAND>) {
    const unlock = await mediaCommandLock.acquire();
    try {
      if (!online.value) {
        console.warn("main: device not online, aborting media command " + mediaCommandData.type);
        return;
      }
      console.debug("main: running media command" + mediaCommandData.type);
      if (isIOSBrowser()) {
        await audioSource?.suspend();
      }
      if ('start' === mediaCommandData.type) {
        mediaSessionStore.startMedia(mediaCommandData.provider, {
          mediaId: mediaCommandData.mediaId,
          playlistId: mediaCommandData.playlistId,
          playlistIndex: mediaCommandData.playlistIndex,
          startSecond: mediaCommandData.second,
        });
        return;
      }
      if ('claim' === mediaCommandData.type) {
        mediaSessionStore.claimMedia(mediaCommandData.provider);
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
    } catch (error) {
      console.error("Error while running media command: ", error);
    } finally {
      if (isIOSBrowser()) {
        try {
          await new Promise(resolve => setTimeout(resolve, 4000));
          await audioSource?.resume();
        } catch (error) {
          console.error("Unable to resume audio source: ", error);
        }
      }
      unlock();
    }
  }
  /**
   *
   */
  function createAudioSink(id: string, volume: number, channels: number, audioPort: MessagePort, onSinkSpeaking: (playing: boolean) => void): WebAudioSink {
    let WebAudioSinkImpl = WebAudioSink;
    if (!isWorkletSupported()) {
      // keep using the processor api on older browsers
      console.warn("No worklet support, falling back to old audio sink implementation");
      WebAudioSinkImpl = WebAudioSinkDeprecated as any;
    }
    const audioContext = getVoiceAudioContext();
    const sink = new WebAudioSinkImpl(id, audioContext, channels, audioPort, volume, (value) => {
      if (value) {
        cancelStopSpeaker();
      } else {
        debouncedStopSpeaker();
      }
    });
    // Sink teardown timeout id
    let speakerOffTimeout: any = null;
    console.debug(`main: starting sink ${id}`);
    if (audioContext.state != 'running') {
      audioContext.resume();
    }
    function stopSpeaker() {
      console.debug(`main: stopping sink ${id}`);
      sink.close();
      activeSinks.delete(id);
      // tear down sink on worker
      worker?.postMessage({ cmd: WorkerInCmd.SPEAK_PORT, id });
      speakerOffTimeout = null;
    }
    function debouncedStopSpeaker() {
      if (!speakerOffTimeout) {
        speakerOffTimeout = setTimeout(() => stopSpeaker(), 500);
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
    activeSinks.set(sink.getId(), sink);
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
