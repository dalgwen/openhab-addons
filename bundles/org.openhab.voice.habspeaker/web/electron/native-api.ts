import { app, ipcMain } from 'electron'
import { join } from 'node:path'
import { readFile } from "node:fs/promises";


export function registerAPIHandlers() {
  ipcMain.handle('check-settings', () => checkConfig);
  ipcMain.handle('setting:speaker-id', () => readConfig('speakerId'));
  ipcMain.handle('setting:oh-url', () => readConfig('ohUrl'));
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
