import { app, BrowserWindow, ipcMain, systemPreferences, dialog } from 'electron'
import { join } from 'node:path';
import { get as getHttp } from 'node:http';
import { get as getHttps } from 'node:https';
import { stringify } from "node:querystring";
import { spawn, ChildProcessWithoutNullStreams } from 'node:child_process';
import { access, constants, readFile, unlink } from "node:fs/promises";
import { readFileSync, accessSync } from 'node:fs';
import { createHash, randomFillSync } from "node:crypto";
import { createServer, AddressInfo } from "node:net";

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
app.on('quit', () => stopLibrespot(true));
const LIBRESPOT_FOLDER = join(__dirname, 'librespot').replace('app.asar', 'app.asar.unpacked');
const LIBRESPOT_CREDENTIALS_FOLDER = join(app.getPath('home'), '.Librespot');
const LIBRESPOT_CREDENTIALS_FILE = join(LIBRESPOT_CREDENTIALS_FOLDER, 'credentials.json');
let spotifyToken: string | undefined;
let librespotDiscoveryPort = 0;
let librespot: ChildProcessWithoutNullStreams | undefined;
let _spotifyId: string | undefined;
let lastNotifiedState = "";
let restartLibrespot = true;
function getLibrespotPlaybackListener(winGetter: () => BrowserWindow | undefined) {
  return (state: string) => {
    if (lastNotifiedState === state) return;
    lastNotifiedState = state;
    console.log("[Librespot] event: " + state);
    winGetter()?.webContents.send('spotify:status', state);
  };
}
async function getSpotifyId() {
  if (_spotifyId) {
    return _spotifyId;
  }
  return _spotifyId = (await tryGetLibrespotInfo())?.deviceID;
}
function stopLibrespot(force = false) {
  restartLibrespot = !force;
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
let restartLibrespotTimeout: any = undefined;
function clearRestartTimeout() {
  if (restartLibrespotTimeout != null) {
    clearTimeout(restartLibrespotTimeout);
    restartLibrespotTimeout = null;
  }
}
type LibrespotLaunchToken = {
  canceled: boolean;
  done: boolean;
};
class LaunchCanceledError extends Error { }
function checkCanceled(loadToken: LibrespotLaunchToken) {
  if (loadToken.canceled) {
    throw new LaunchCanceledError();
  }
}
let promiseToken: LibrespotLaunchToken;
async function startLibrespot(label: string, onChange: (state: string) => void) {
  if (promiseToken && !promiseToken.done) {
    // ensure previous launch stops
    promiseToken.canceled = true;
  }
  promiseToken = { canceled: false, done: false };
  launchLibrespot(label, onChange, promiseToken).catch(err => {
    if (err instanceof LaunchCanceledError) {
      console.log("Previous Librespot launch aborted");
    } else {
      console.error(err);
    }
  });
}
async function launchLibrespot(label: string, onChange: (state: string) => void, loadToken: LibrespotLaunchToken) {
  clearRestartTimeout();
  stopLibrespot();
  let userInfo: SpotifyUserInfo;
  console.log("Loading Spotify account info");
  let retries = 0;
  if (spotifyToken == null) {
    console.error("Unable to authenticate Librespot, missing token");
  }
  while (retries < 5) {
    retries++;
    try {
      userInfo = await getUserInfo();
      break;
    } catch (error: any) {
      console.warn(error.message);
      await sleep(500);
    }
    checkCanceled(loadToken);
  }
  if (!userInfo) {
    console.error("Spotify get user info failed, could not authenticate")
    return;
  }
  let librespotAuthorized = await fileExists(LIBRESPOT_CREDENTIALS_FILE);
  checkCanceled(loadToken);
  if (librespotAuthorized) {
    // check credentials match current user
    let credentialsOk = false;
    try {
      const librespotCredentials = JSON.parse((await readFile(LIBRESPOT_CREDENTIALS_FILE)).toString()) as { username: string, auth_type: number, auth_data: string };
      credentialsOk = librespotCredentials.username === userInfo.id
    } catch (err) {
      console.warn("Error parsing Librespot credentials: ", err);
    }
    checkCanceled(loadToken);
    if (!credentialsOk) {
      console.log("Removing invalid Librespot credentials");
      librespotAuthorized = false;
      try {
        await unlink(LIBRESPOT_CREDENTIALS_FILE);
      } catch (err) {
        console.error("Unable to delete invalid Librespot credentials", err);
        return;
      }
      checkCanceled(loadToken);
    } else {
      console.log("Found valid Librespot credentials");
    }
  }
  console.log("Starting Librespot");
  librespotDiscoveryPort = await getPortFree();
  checkCanceled(loadToken);
  librespot = spawn(getLibrespotExecutable(), [
    "-n", label,
    "-b", "320",
    "-C", LIBRESPOT_CREDENTIALS_FOLDER,
    "-z", librespotDiscoveryPort.toString(),
    "-v"
  ]);
  function processLibrespotOutput(data: string) {
    const lines = (data != null ? data.toString() : '').split('\n');
    for (const line of lines) {
      if (line.includes("Sending status to server:")) {
        if (line.includes("[kPlayStatusPlay]")) {
          onChange('play');
        } else if (line.includes("[kPlayStatusPause]")) {
          onChange('pause');
        } else if (line.includes("[kPlayStatusStop]")) {
          onChange('stop');
        }
      } else if (line.includes("command=Stop")) {
        onChange('stop');
      }
    }
  }
  librespot.stdout.on("data", processLibrespotOutput);
  librespot.stderr.on("data", processLibrespotOutput);
  librespot.on('error', (error) => {
    console.log(`Librespot error: ${error.message}`);
  });
  let exited = false;
  librespot.on("close", code => {
    exited = true;
    console.error(`Librespot exited with code ${code}`);
    if (code != 0 && restartLibrespot) {
      console.log("Scheduling Librespot start");
      clearRestartTimeout();
      restartLibrespotTimeout = setTimeout(() => {
        restartLibrespotTimeout = null;
        startLibrespot(label, onChange);
      }, 5000);
    }
  });
  if (!librespotAuthorized) {
    console.log("Authenticating Librespot");
    console.log("Checking connection to Librespot");
    retries = 0;
    while (_spotifyId == null && retries < 20) {
      if (exited) return;
      await sleep(1000);
      retries++;
      await getSpotifyId();
      checkCanceled(loadToken);
    }
    if (_spotifyId == null) {
      console.error("Unable to connect to Librespot");
      return;
    }
    console.log("Librespot running with device_id:", _spotifyId);
    retries = 0;
    while (retries < 5) {
      if (exited) return;
      retries++;
      checkCanceled(loadToken);
      try {
        console.log("Authentication attempt success:", await authenticateLibrespot(userInfo.id, spotifyToken));
        console.log("Librespot was correctly authenticated");
        loadToken.done = true;
        return;
      } catch (error: any) {
        console.warn(error.message);
        await sleep(1000);
      }
    }
    loadToken.done = true;
    throw new Error("Unable to authenticate librespot");
  } else {
    loadToken.done = true;
  }
}
function getLibrespotExecutable() {
  switch (process.platform) {
    case "linux":
    case "darwin":
      return join(LIBRESPOT_FOLDER, 'librespot');
    case "win32":
      return join(LIBRESPOT_FOLDER, 'librespot.exe');
    default:
      return;
  }
}
async function isLibrespotAvailable() {
  const execPath = getLibrespotExecutable();
  return fileExists(execPath);
}
async function fileExists(path: string) {
  if (path) {
    try {
      await access(path, constants.F_OK);
      return true;
    } catch (error) {
    }
  }
  return false;
}
export type LibrespotDeviceInfo = {
  deviceID: string;
};
export type SpotifyUserInfo = {
  id: string;
  product: "premium";
  type: "user";
  country: string;
  display_name: string;
  email: string;
  uri: string;
};
async function getUserInfo() {
  return new Promise<SpotifyUserInfo>((resolve, reject) => {
    getHttps("https://api.spotify.com/v1/me", { headers: { "Authorization": `Bearer ${spotifyToken}`, "Content-Type": "application/json" } }, res => {
      let data = [];
      res.on('data', chunk => {
        data.push(chunk);
      });
      res.on('end', () => {
        if (res.statusCode > 199 && res.statusCode < 400) {
          try {
            const respBody = Buffer.concat(data).toString();
            const info = JSON.parse(respBody) as SpotifyUserInfo;
            return resolve(info);
          } catch (error) {
            return reject(error);
          }
        }
        reject(new Error(`Get SpotifyUserInfo failed with status: ${res.statusCode} ${res.statusMessage}`));
      });
    });
  });
}
async function tryGetLibrespotInfo() {
  return new Promise<LibrespotDeviceInfo | undefined>((resolve) => {
    getHttp(`http://127.0.0.1:${librespotDiscoveryPort}/?action=getInfo`, res => {
      let data = [];
      res.on('data', chunk => {
        data.push(chunk);
      });
      res.on('end', () => {
        if (res.statusCode == 200) {
          try {
            const respBody = Buffer.concat(data).toString();
            const info = JSON.parse(respBody) as LibrespotDeviceInfo;
            return resolve(info);
          } catch (error) {
            console.log('Error parsing Librespot info: ', error.message);
          }
        }
        resolve(undefined);
      });
    }).on('error', err => {
      console.log('Error: ', err.message);
      resolve(undefined);
    });
  });
}
export async function authenticateLibrespot(username: string, token: string) {
  return new Promise<boolean>((resolve, reject) => {
    const deviceId = createHash('sha1').update(Buffer.from("spotify-connect")).digest("hex");
    const loginId = randomFillSync(Buffer.alloc(16)).toString("hex");
    const query = {
      "action": "addUser",
      "userName": username,
      "blob": token,
      "clientKey": "",
      "deviceId": deviceId,
      "deviceName": "",
      "loginId": loginId,
      "tokenType": "accesstoken"
    };
    getHttp({
      host: '127.0.0.1',
      port: librespotDiscoveryPort,
      path: '/?' + stringify(query),
      method: 'POST',
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    }, res => {
      res
        .on('data', () => { })
        .on('end', () => {
          try {
            resolve(res.statusCode === 200);
          } catch (error) {
            reject(new Error(`Librespot authentication was rejected with status: ${res.statusCode} ${res.statusMessage}`));
          }
        });
    }).on('error', err => {
      reject(new Error(`Librespot authentication was rejected with status: ${err?.message}`));
    });
  });
}
async function getPortFree() {
  return new Promise<number>(res => {
    const srv = createServer();
    srv.listen(0, () => {
      const port = (srv.address() as AddressInfo).port;
      srv.close((err) => res(port))
    });
  })
}
async function sleep(ms: number) {
  await new Promise(resolve => setTimeout(resolve, ms));
}
// handle settings
const SPEAKER_CONFIG_PATH = join(app.getPath('home'), '.HABSpeaker', 'settings.json');
type ConfigFile = {
  ohUrl: string,
  speakerId: string;
  ohToken?: string;
};
export function isConfigLoaded() {
  return !!config;
}
function loadConfigFromFile() {
  try {
    try {
      accessSync(SPEAKER_CONFIG_PATH, constants.F_OK);
    } catch (error) {
      throw new Error("Local settings file not found, please ensure it is in place.")
    }
    let configFile: ConfigFile;
    try {
      configFile = JSON.parse(readFileSync(SPEAKER_CONFIG_PATH).toString()) as ConfigFile;
    } catch (error) {
      throw new Error("Unable to parse speaker settings as json.")
    }
    if (!configFile.speakerId) {
      // TODO: validate alphanumeric with dashes
      throw new Error('Incorrect speakerId settings property');
    }
    if (!configFile.ohUrl) {
      // TODO: validate url
      throw new Error('Incorrect ohUrl settings property');
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
  dialog.showErrorBox("Configuration Error", error.message ?? "Unable to read speaker config");
  app.exit(1);
}