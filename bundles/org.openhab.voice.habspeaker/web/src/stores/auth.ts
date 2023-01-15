import { defineStore } from "pinia";
import axios, { AxiosError } from "axios";
import { useIOStore } from "./io";
import { useSettingsStore } from "./settings";
import { OHAuthHelper } from "../utils/openhab-auth-helper";
import { getPlatformName, getServerToken, getUrlOpenHAB } from "../platforms";
import { useSpotifyPlayerStore } from "./media-players/spotify-player";
import router from "../router";
export const useAuthStore = defineStore("auth", () => {
  const ioStore = useIOStore();
  const spotifyStore = useSpotifyPlayerStore();
  const { getSpeakerId } = useSettingsStore();
  const ohAuthHelper = new OHAuthHelper({ path: '/habspeaker', ohUrl: getUrlOpenHAB });
  let persistentToken: string | null = null;
  function getAccessToken() {
    return persistentToken ?? ohAuthHelper.getAccessToken() ?? '';
  }
  async function getUIConfig(): Promise<UIConfig> {
    const headers = {
      accept: "application/json",
    } as { [key: string]: string };
    if (getAccessToken().length) { headers["Authorization"] = "Bearer " + getAccessToken(); }
    return (await axios.get<UIConfig>(`${await getUrlOpenHAB()}/rest/habspeaker/config/${await getSpeakerId()}`)).data;
  }
  function unauthorized() {
    console.debug("Unauthorized, redirecting to login");
    if (!(import.meta as any).env.DEV) {
      ohAuthHelper.authorize();
    } else {
      console.warn("Login redirection disabled in dev mode");
    }
  }
  async function authorize() {
    try {
      const authorized = await ohAuthHelper.tryExchangeAuthorizationCode();
      await ohAuthHelper.refreshAccessToken((err, data) => {
        if (err) {
          return unauthorized();
        } else if (data) {
          ioStore.setAuthToken(data.access_token);
        }
      }, !authorized);
    } catch (error) {
      console.error(error);
      return unauthorized();
    }
  }
  // check security and redirect to login
  (async function () {
    let requireCredentials = false;
    let startSpotify = false;
    persistentToken = await getServerToken();
    try {
      const { spotifyEnabled } = await getUIConfig();
      requireCredentials = false;
      startSpotify = spotifyEnabled;
    } catch (error) {
      if (error instanceof AxiosError && error.response?.status === 401) {
        if (persistentToken != null) {
          return await router.replace("/unauthorized");
        }
        requireCredentials = true;
      } else {
        throw error;
      }
    }
    if (requireCredentials) {
      console.debug("Authorization required!");
      if((await getPlatformName()) == 'electron') {
        return await router.replace("/unauthorized");
      }
      await authorize();
      const { spotifyEnabled } = await getUIConfig();
      startSpotify = spotifyEnabled;
    }
    if (startSpotify) {
      spotifyStore.initSpotify()
        .then(() => console.log('Spotify initialized'))
        .catch(err => console.error("Spotify error: ", err));
    }
  })();
  return { getUIConfig, getAccessToken, authorize };
});

type UIConfig = {
  spotifyEnabled: boolean;
  label: string;
};