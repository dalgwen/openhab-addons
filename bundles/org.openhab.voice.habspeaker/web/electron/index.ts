import { app, BrowserWindow, shell } from 'electron';
import { release } from 'node:os';
import { join } from 'node:path';
import { registerAPIHandlers, requestPermissions } from './native-api';
const DIST = join(__dirname, './habspeaker');
if (process.platform === 'win32') {
  // Disable GPU Acceleration for Windows 7
  if (release().startsWith('6.1')) app.disableHardwareAcceleration();

  // Set application name for Windows 10+ notifications
  app.setAppUserModelId(app.getName());
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
  process.exit(0)
}

let win: BrowserWindow | null = null
const preload = join(__dirname, 'preload.js');
const indexHtml = join(DIST, 'index.html');

async function createWindow() {
  win = new BrowserWindow({
    title: 'HAB Speaker',
    icon: join(DIST, 'favicon.svg'),
    webPreferences: {
      preload,
      nodeIntegration: false,
      contextIsolation: true,
    },
  });
  // remove menu options
  win.removeMenu();
  if (process.env.VITE_DEV_SERVER_URL) {
    win.loadURL(process.env.VITE_DEV_SERVER_URL)
    win.webContents.openDevTools();
  } else {
    win.loadFile(indexHtml);
  }
  // handle open external links
  win.webContents.setWindowOpenHandler(({ url }) => {
    // const ohUrl = await getOhUrl();
    // if (ohUrl.length && url.startsWith(ohUrl + '/auth')) return { action: 'allow' };
    if (url.startsWith('https:')) shell.openExternal(url)
    return { action: 'deny' }
  })
}
function registerHABSpeakerHandlers() {
  return registerAPIHandlers(() => win);
}
// start the application
app.whenReady()
  .then(requestPermissions)
  .then(registerHABSpeakerHandlers)
  .then(createWindow);

app.on('window-all-closed', () => {
  win = null
  if (process.platform !== 'darwin') app.quit()
})

app.on('second-instance', () => {
  if (win) {
    // Focus on the main window if the user tried to open another
    if (win.isMinimized()) win.restore()
    win.focus()
  }
})

app.on('activate', () => {
  const allWindows = BrowserWindow.getAllWindows()
  if (allWindows.length) {
    allWindows[0].focus()
  } else {
    createWindow()
  }
});