import { app, BrowserWindow, ipcMain, systemPreferences } from 'electron'
import { join } from 'node:path';
import { get } from 'node:http';
import { spawn, ChildProcessWithoutNullStreams } from 'node:child_process';
import { access, constants } from "node:fs/promises";
import { readFileSync } from 'node:fs';
export function getOhUrl() {
  return config.ohUrl;
}
export async function requestPermissions() {
  if (systemPreferences.getMediaAccessStatus("microphone") !== 'granted'
    && !await systemPreferences.askForMediaAccess("microphone")) {
    console.error("HAB Speaker needs microphone access");
    app.exit(1);
  }
}

export function registerAPIHandlers(winGetter: () => BrowserWindow | undefined) {
  ipcMain.handle('setting:speaker-id', () => config.speakerId);
  ipcMain.handle('setting:oh-url', () => config.ohUrl);
  ipcMain.handle('setting:oh-token', () => config.ohToken);
  ipcMain.handle('spotify:available', () => isLibrespotAvailable());
  ipcMain.handle('spotify:start', (_, name: string) => startLibrespot(name, getLibrespotPlaybackListener(winGetter)));
  ipcMain.handle('spotify:stop', () => stopLibrespot());
  ipcMain.handle('spotify:id', () => getSpotifyId());
  ipcMain.handle('spotify:token', (_, accessToken) => spotifyToken = accessToken);
}

// handle librespot
let spotifyToken = "";
const LIBRESPOT_DISCOVERY_PORT = 9298;
let librespot: ChildProcessWithoutNullStreams | undefined;
app.on('quit', stopLibrespot);
let _spotifyId: string | undefined;
let notifySpotifyTimeout: any = null;

function getLibrespotPlaybackListener(winGetter: () => BrowserWindow | undefined) {
  return (state: string) => {
    if (notifySpotifyTimeout) clearTimeout(notifySpotifyTimeout);
    notifySpotifyTimeout = setTimeout(() => {
      winGetter()?.webContents.send('spotify:status', state);
    }, 1000);
  };
}
async function getSpotifyId() {
  if (_spotifyId) {
    return _spotifyId;
  }
  return new Promise<string | undefined>((resolve) => {
    get(`http://127.0.0.1:${LIBRESPOT_DISCOVERY_PORT}/?action=getInfo`, res => {
      let data = [];
      res.on('data', chunk => {
        data.push(chunk);
      });
      res.on('end', () => {
        try {
          const respBody = Buffer.concat(data).toString();
          const info = JSON.parse(respBody) as { deviceID: string };
          _spotifyId = info.deviceID;
          resolve(info.deviceID);
        } catch (error) {
          console.log('Error: ', error.message);
          resolve(undefined);
        }
      });
    }).on('error', err => {
      console.log('Error: ', err.message);
      resolve(undefined);
    });
  });
}
async function stopLibrespot() {
  if (librespot) {
    console.log("Stopping Librespot...");
    librespot.stdout.destroy();
    librespot.stderr.destroy();
    if (librespot.kill('SIGKILL')) {
      console.log("Librespot stopped!");
    }
    librespot = undefined;
    _spotifyId = undefined;
  }
}
async function startLibrespot(label: string, onChange: (state: string) => void) {
  await stopLibrespot();
  librespot = spawn(getLibrespotExecutable().replace('app.asar', 'app.asar.unpacked'), [
    "-n", label,
    // "-b", "320",
    "-C", join(app.getPath('home'), '.Librespot'),
    "-z", LIBRESPOT_DISCOVERY_PORT.toString(),
    "-v"
  ]);
  librespot.stdout.on("data", data => {
    console.log(`librespot: ${data}`);
  });
  librespot.stderr.on("data", (data: string) => {
    //   console.log(`librespot: ${data}`);
    if (data.includes("Sending status to server: [kPlayStatusPlay]")) {
      console.log("main: spotify resumed");
      onChange('play');
    } else if (data.includes("Sending status to server: [kPlayStatusPause]")) {
      console.log("main: spotify paused");
      onChange('pause');
    } else if (data.includes("Sending status to server: [kPlayStatusStop]")) {
      console.log("main: spotify stopped");
      onChange('stop');
    }
  });
  librespot.on('error', (error) => {
    console.log(`librespot error: ${error.message}`);
  });

  librespot.on("close", code => {
    console.log(`librespot exited with code ${code}`);
  });
}
function getLibrespotExecutable() {
  switch (process.platform) {
    case "linux":
    case "darwin":
      return join(process.env.LIBRESPOT_FOLDER, 'librespot');
    default:
      return;
  }
}
async function isLibrespotAvailable() {
  const execPath = getLibrespotExecutable();
  if (execPath) {
    try {
      await access(execPath, constants.F_OK);
      return true;
    } catch (error) {
    }
  }
  return false;
}
// handle settings
const configPath = join(app.getPath('home'), '.HABSpeaker', 'settings.json');
type ConfigFile = {
  ohUrl: string,
  speakerId: string;
  ohToken?: string;
};
function loadConfigFromFile() {
  try {
    const configFile = JSON.parse(readFileSync(configPath).toString()) as ConfigFile;
    if (!configFile.speakerId) {
      // TODO: validate alphanumeric with dashes
      throw new Error('Incorrect speakerId');
    }
    if (!configFile.ohUrl) {
      // TODO: validate url
      throw new Error('Incorrect openHAB url');
    }
    if (!configFile.ohToken) {
      // TODO: validate token
    }
    return configFile;
  } catch (error) {
    throw error;
  }
}
// load speaker config
let config: ConfigFile = null;
try {
  config = loadConfigFromFile();
} catch (error) {
  console.error("Unable to read speaker config: ", error);
  app.exit(1);
}