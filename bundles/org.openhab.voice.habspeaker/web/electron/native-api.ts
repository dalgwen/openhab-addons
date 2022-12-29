import { app, BrowserWindow, ipcMain } from 'electron'
import { join } from 'node:path';
import { get } from 'node:http';
import { spawn, ChildProcessWithoutNullStreams } from 'node:child_process';
import { readFile, access, constants } from "node:fs/promises";


export function registerAPIHandlers(winGetter: () => BrowserWindow | undefined) {
  ipcMain.handle('check-settings', () => checkConfig);
  ipcMain.handle('setting:speaker-id', () => readConfig('speakerId'));
  ipcMain.handle('setting:oh-url', () => readConfig('ohUrl'));
  ipcMain.handle('spotify:available', () => isLibrespotAvailable());
  ipcMain.handle('spotify:start', (ev, name: string) => startLibrespot(name, getLibrespotPlaybackListener(winGetter)));
  ipcMain.handle('spotify:stop', () => stopLibrespot());
  ipcMain.handle('spotify:id', () => getSpotifyId());
}

// handle librespot
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
  accessToken?: string;
};
let config: ConfigFile | undefined;
async function loadConfigFromFile() {
  try {
    const configFile = JSON.parse((await readFile(configPath)).toString()) as ConfigFile;
    if (!configFile.speakerId) {
      // TODO: validate alphanumeric with dashes
      throw new Error('Incorrect speakerId');
    }
    if (!configFile.ohUrl) {
      // TODO: validate url
      throw new Error('Incorrect openHAB url');
    }
    if (!configFile.accessToken) {
      // TODO: validate token
    }
    return configFile;
  } catch (error) {
    console.log(error);
    throw error;
  }
}
async function checkConfig() {
  try {
    return !!(await loadConfigFromFile())
  } catch (error) {
    console.error(error);
    return false;
  }
}
async function readConfig<T extends keyof ConfigFile>(key: T): Promise<ConfigFile[T]> {
  return (await loadConfigFromFile())[key];
}
