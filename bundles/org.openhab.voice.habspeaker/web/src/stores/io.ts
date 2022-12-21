import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { useScreenSaverStore } from "./screen-saver";
import { useMediaSessionStore } from "./media-players/media-session";
import { useSpotifyPlayerStore } from "./media-players/spotify-player";
import { WebAudioSource } from "../utils/web-source";
import { WebAudioSink } from "../utils/web-sink";
import { MediaStateCmd, WorkerInCmd, WorkerOutCmd, WorkerOutCmdType } from "../utils/io-types";
export const useIOStore = defineStore("io", () => {
  let audioContext: AudioContext | null = null;
  let micStreaming = false;
  let currentSpeaking = false;
  const activeSinks = new Map<string, WebAudioSink>();
  function startVoiceAudioContext() {
    if (!audioContext) {
      // initialize to 16000 to avoid resampling, some browsers can ignore this
      const voiceSampleRate = 16000;
      var isChromium = !!(window as any).chrome;
      let options: AudioContextOptions = {};
      if(isChromium) {
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
  const spotifyStore = useSpotifyPlayerStore();
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController } = storeToRefs(mediaSessionStore);
  const { awakeScreenSaver, setScreenSaverTime } = useScreenSaverStore();
  // state
  const listening = ref(false);
  const speaking = ref(false);
  const online = ref(false);
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
    if (spotifyStore.isEnabled()) {
      if (value) {
        spotifyStore.connect()
          .then((connected) => console.debug("Spotify is connected: " + connected))
          .catch(() => console.error("Error connecting to spotify"));
      } else if (!value) {
        spotifyStore.disconnect()
          .then(() => console.debug("Spotify is disconnected"))
          .catch(() => console.error("Error connecting to spotify"));
      }
    }
    if (!value) {
      mediaSessionStore.stopMedia();
    }
    online.value = value;
  }
  function updateSpotifyToken(token: string) {
    spotifyStore.updateToken(token);
  }
  // worker setup
  let worker: Worker | null = null;
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
    postToWorker(WorkerInCmd.ON_SPOT);
  }
  function resetConnection(id: string) {
    postToWorker(WorkerInCmd.RESET_CONNECTION, { id });
  }
  function sendMediaState(state: MediaStateCmd) {
    postToWorker(WorkerInCmd.MEDIA_STATE, state);
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
    const audioSource = new WebAudioSource(getVoiceAudioContext(), (buffers) => worker?.postMessage({ cmd: WorkerInCmd.LISTEN, buffers }));
    await audioSource.resume();
    // microphone stream checker, to keep the stream alive on undetected disconnections  
    setInterval(audioSource.resume.bind(audioSource), 10000);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) {
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
              const { sinkVolume, remoteSpot, screenSaverTime, spotifyToken } = ev.data as WorkerOutCmdType<typeof command>;
              if (sinkVolume != null) {
                sinkConfig.volume = sinkVolume;
              }
              remoteSpotMode = !!remoteSpot;
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
              stopMicStreaming();
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
                mediaSessionStore.startMedia(mediaCommandData.provider, mediaCommandData.id);
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
                  mediaSessionCtrl.stop();
                  mediaSessionStore.stopMedia();
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
        worker.postMessage({
          cmd: WorkerInCmd.INITIALIZE,
          id,
          token,
          sampleRate: getVoiceAudioContext().sampleRate,
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
