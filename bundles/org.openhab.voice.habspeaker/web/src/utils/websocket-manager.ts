import { WebAudioSink } from "./web-sink";
import { WebAudioSource } from "./web-source";
import { WorkerInCmd, WorkerOutCmd, WorkerOutCmdType } from "./websocket-worker";
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

/**@type {Map<string, {audioCache: AudioCache, setVolume: (value:number) => void, speaking: boolean}>} */
const activeSinks = new Map<string, WebAudioSink>();

/**@type {Worker} */
let worker: Worker | null = null;

type WorkerActions = {
  setListening: (value: boolean) => void,
  setSpeaking: (value: boolean) => void,
  setOnline: (value: boolean) => void,
  setScreenSaverTime: (value: number) => void,
  getMediaCtrl: () => MediaSessionCtrl | null,
  startMedia: (provider: string, media: string) => void,
  stopMedia: () => void,
  updateSpotifyToken: (token: string) => void
};
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
  let remoteSpotMode = false;
  startAudioContext();
  const audioSource = new WebAudioSource(getAudioContext(), (buffers) => worker?.postMessage({ cmd: WorkerInCmd.LISTEN, buffers }));
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
      worker = new Worker(new URL("./websocket-worker.ts", import.meta.url), {
        name: "habspeaker-worker",
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
            const { sinkVolume, sinkStereo, remoteSpot, screenSaverTime, spotifyToken, label } = ev.data as WorkerOutCmdType<typeof command>;
            if (sinkVolume != null) {
              sinkConfig.volume = sinkVolume;
            }
            if (sinkStereo != null) {
              sinkConfig.stereo = sinkStereo;
            }
            remoteSpotMode = !!remoteSpot;
            if (screenSaverTime != null && !isNaN(screenSaverTime)) {
              actions.setScreenSaverTime(screenSaverTime);
            }
            if (spotifyToken) {
              actions.updateSpotifyToken(spotifyToken);
            }
            break;
          case WorkerOutCmd.INITIALIZED:
            actions.setOnline(true);
            if (remoteSpotMode) {
              console.debug("remote spot enabled, starting mic streaming");
              startMicStreaming();
            }
            break;
          case WorkerOutCmd.OFFLINE:
            sinkConfig = { ...defaultSinkConfig };
            remoteSpotMode = false;
            actions.setListening(false);
            actions.setOnline(false);
            stopMicStreaming();
            break;
          case WorkerOutCmd.SPEAK: {
            const speakData = ev.data as WorkerOutCmdType<typeof command>;
            let sinkContext = activeSinks.get(speakData.id);
            if (!sinkContext) {
              sinkContext = createAudioSink(speakData.id, sinkConfig.volume, sinkConfig.stereo, onSinkSpeaking);
              activeSinks.set(sinkContext.getId(), sinkContext);
            }
            sinkContext.playAudio(speakData.buffer);
            break;
          }
          case WorkerOutCmd.START_LISTENING:
            actions.setListening(true);
            if (!remoteSpotMode) {
              startMicStreaming();
            }
            break;
          case WorkerOutCmd.STOP_LISTENING:
            actions.setListening(false);
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
              actions.startMedia(mediaCommandData.provider, mediaCommandData.id);
              return;
            }
            const mediaSessionCtrl = actions.getMediaCtrl();
            if (!mediaSessionCtrl) {
              console.log("Media is not started");
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
                actions.stopMedia();
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
                mediaSessionCtrl.setVolume(mediaCommandData.level);
                break;
              default:
                console.error("Unsupported media command: ", mediaCommandData);
            }
            break;
          case WorkerOutCmd.SPOTIFY_TOKEN:
            const spotifyTokenData = ev.data as WorkerOutCmdType<typeof command>;
            actions.updateSpotifyToken(spotifyTokenData.token);
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
      actions.setSpeaking(speakingValue);
    }
  }
}
export interface MediaSessionCtrl {
  getId(): string;
  getMediaId(): Promise<string>;
  getAwakeScreen(): boolean;
  getVolume(): Promise<number>;
  setVolume(value: number): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  stop(): Promise<void>;
  previous(): Promise<void>;
  next(): Promise<void>;
  seek(second: number): Promise<void>;
  getCurrentSecond(): Promise<number>;
  getTotalSeconds(): Promise<number>;
  getPlaybackState(): Promise<PlaybackState>;
}
export enum MediaProvider {
  YOUTUBE = 'youtube',
  SPOTIFY = 'spotify',
  WEB_AUDIO = 'web-audio',
  WEB_VIDEO = 'web-video',
}
export enum PlaybackState {
  PLAYING = 'playing',
  PAUSED = 'paused',
  STOPPED = 'stopped',
  BUFFERING = 'buffering',
}