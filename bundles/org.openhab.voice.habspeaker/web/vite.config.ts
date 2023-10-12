import { defineConfig, PluginOption } from "vite";
import ConditionalCompile from "vite-plugin-conditional-compiler";
import pkg from './package.json';
let isDevelopment = false;
let isProduction = false;
// https://vitejs.dev/config/
export default defineConfig(async ({ command, mode }) => {
  isDevelopment = command == "serve" || process.env.NODE_ENV === "development"
  isProduction = !isDevelopment;
  const plugins: PluginOption[] = [ConditionalCompile()];
  let baseUrl: string | undefined;
  const envMode = isProduction ? 'production' : 'development';
  const OH_PROXY_URL = process.env.OH_PROXY ?? "http://127.0.0.1:8080";
  if (command == "serve") {
    (process.env as { [key: string]: unknown }).VITE_DEV_SERVER_URL = "http://localhost:5173";
    (process.env as { [key: string]: unknown }).VITE_DEV_OH_PROXY = OH_PROXY_URL;
  }
  switch (mode) {
    case "electron": {
      console.log(`Building ${envMode} HABSpeaker UI Electron bundle`);
      const { rmSync } = await import('node:fs');
      rmSync('dist-electron', { recursive: true, force: true });
      plugins.push(await getElectronPlugin());
      break;
    }
    case "capacitor":
      console.log(`Building ${envMode} HABSpeaker UI Capacitor bundle`);
      break;
    case "pwa":
      console.log(`Building ${envMode} HABSpeaker UI PWA bundle`);
      baseUrl = "/habspeaker/";
      plugins.push(await getPWAPlugin());
      break;
    default:
      console.log("Unsupported mode");
      process.exit(1);
  }
  return {
    base: baseUrl,
    plugins,
    envDir: 'env',
    build: {
      sourcemap: isDevelopment,
      minify: isProduction,
    },
    server: {
      port: 5173,
      proxy: {
        "/ws/habspeaker": {
          target: OH_PROXY_URL.replace("http:", "ws:").replace("https:", "wss:"),
          ws: true,
        },
        "/rest": {
          target: OH_PROXY_URL,
        },
      },
    },
    clearScreen: false,
  };
});

// Platform Plugins
async function getPWAPlugin() {
  const { VitePWA } = (await import("vite-plugin-pwa"));
  return VitePWA({
    registerType: 'autoUpdate',
    manifest: {
      name: "HABSpeaker",
      short_name: "HABSpeaker",
      description: "Dialog processing over WebSocket for openHAB",
      display: "fullscreen",
      background_color: "#ff6600",
      theme_color: "#000000",
      icons: [
        {
          "src": "/habspeaker/icons/icon-72x72.png",
          "sizes": "72x72",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-192x192.png",
          "sizes": "192x192",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-256x256.png",
          "sizes": "256x256",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-384x384.png",
          "sizes": "384x384",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-512x512.png",
          "sizes": "512x512",
          "type": "image/png"
        }
      ]
    },
  });
}

async function getElectronPlugin() {
  const electron = (await import('vite-plugin-electron')).default;
  const externalDeps = [...Object.keys(pkg.dependencies), ...Object.keys(pkg.optionalDependencies)];
  return electron([
    {
      // Main-Process entry file of the Electron App.
      entry: 'electron/index.ts',
      onstart(options) {
        options.startup(['dist-electron/index.js', '--no-sandbox']);
      },
      vite: {
        build: {
          sourcemap: isDevelopment,
          minify: isProduction,
          outDir: 'dist-electron/',
          rollupOptions: {
            external: externalDeps,
          },
        },
        clearScreen: false,
      },
    },
    {
      entry: 'electron/preload.ts',
      onstart(options) {
        // Notify the Renderer-Process to reload the page when the Preload-Scripts build is complete, 
        // instead of restarting the entire Electron App.
        options.reload()
      },
      vite: {
        build: {
          sourcemap: isDevelopment,
          minify: isProduction,
          outDir: 'dist-electron/',
          rollupOptions: {
            external: externalDeps,
          },
        },
        clearScreen: false,
      },
    },
  ]);
}