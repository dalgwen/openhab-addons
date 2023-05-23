import { app, BrowserWindow, ipcMain, systemPreferences, powerSaveBlocker } from 'electron'
import { join } from 'node:path';
import { access, constants, readFile, writeFile, mkdir } from "node:fs/promises";

export async function getOhUrl() {
  return (await getLocalConfig())?.ohUrl ?? '';
}
export async function requestPermissions() {
  if (systemPreferences.getMediaAccessStatus("microphone") !== 'granted'
    && !await systemPreferences.askForMediaAccess("microphone")) {
    console.error("HABSpeaker needs microphone access");
    app.exit(1);
  }
}

export function registerAPIHandlers(winGetter: () => BrowserWindow | undefined) {
  ipcMain.handle('setting:commit', async (_, localSettings) => await saveConfigFile(localSettings));
  ipcMain.handle('setting:speaker-id', async () => (await getLocalConfig())?.speakerId);
  ipcMain.handle('setting:oh-url', async () => (await getLocalConfig())?.ohUrl);
  ipcMain.handle('setting:oh-token', async () => (await getLocalConfig())?.ohToken);
  ipcMain.handle('sleep:block', async (_, value) => await sleepBlocker(value));
}

// handle screen lock
let lockRef: number | undefined;
async function sleepBlocker(value: boolean) {
  if (value && lockRef == null) {
    lockRef = powerSaveBlocker.start('prevent-app-suspension');
    console.log("device sleep locked: " + powerSaveBlocker.isStarted(lockRef));
  } else if (!value && lockRef != null) {
    const lock = lockRef;
    lockRef = null;
    powerSaveBlocker.stop(lock);
  }

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

// handle settings
const SPEAKER_CONFIG_FOLDER = join(app.getPath('home'), '.HABSpeaker');
const SPEAKER_CONFIG_PATH = join(SPEAKER_CONFIG_FOLDER, 'settings.json');
type ConfigFile = {
  ohUrl: string,
  speakerId: string;
  ohToken?: string;
};
export function isConfigLoaded() {
  return !!config;
}
async function saveConfigFile(localSettings: any) {
  if (!await fileExists(SPEAKER_CONFIG_FOLDER)) {
    await mkdir(SPEAKER_CONFIG_FOLDER);
  }
  const settingsJSON = JSON.stringify(localSettings, null, 2);
  await writeFile(SPEAKER_CONFIG_PATH, settingsJSON, { flag: 'w' });
}
async function loadConfigFromFile() {
  try {
    try {
      await access(SPEAKER_CONFIG_PATH, constants.F_OK);
    } catch (error) {
      throw new Error("Local settings file not found, please ensure it is in place.")
    }
    let configFile: ConfigFile;
    try {
      configFile = JSON.parse((await readFile(SPEAKER_CONFIG_PATH)).toString()) as ConfigFile;
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
    return configFile;
  } catch (error) {
    throw error;
  }
}
// load speaker config
let config: ConfigFile = null;
async function getLocalConfig() {
  if (!config)
    await tryLoadConfig();
  return config;
}
tryLoadConfig();
async function tryLoadConfig() {
  try {
    config = await loadConfigFromFile();
  } catch (error) {
    console.error("Unable to read speaker config: ", error);
  }
}

