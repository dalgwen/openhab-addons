import { defineStore } from "pinia";
import axios, { AxiosError } from "axios";
import { useIOStore } from "./io";
import { useSettingsStore } from "./settings";
import { OHAuthHelper } from "../utils/openhab-auth-helper";
import { getUrlOpenHAB } from "../platforms";
export const useAuthStore = defineStore("auth", () => {
  const ioStore = useIOStore();
  const { getSpeakerId } = useSettingsStore();
  const ohAuthHelper = new OHAuthHelper({ path: '/habspeaker', ohUrl: getUrlOpenHAB });
  function getAccessToken() {
    return ohAuthHelper.getAccessToken();
  }
  async function getUIConfig(): Promise<UIConfig> {
    try {
      return (await axios.get<UIConfig>(`${await getUrlOpenHAB()}/rest/habspeaker/config/${await getSpeakerId()}`)).data;
    } catch (error) {
      if (error instanceof AxiosError && error.response?.status === 401) {
        return { secure: true, spotifyEnabled: false, label: "" };
      }
      throw error;
    }
  }
  async function getSpeakerCookie() {
    const headers = {
      "content-type": "application/x-www-form-urlencoded",
      accept: "application/json",
    } as { [key: string]: string };
    if (ohAuthHelper.hasAccessToken()) { headers["Authorization"] = "Bearer " + ohAuthHelper.getAccessToken(); }
    const refreshToken = ohAuthHelper.getRefreshToken();
    if (!refreshToken) {
      throw new Error('Missing refresh token.');
    }
    await axios
      .post(`${await getUrlOpenHAB()}/rest/habspeaker/cookie`, `${encodeURIComponent("refresh_token")}=${encodeURIComponent(refreshToken)}`, { headers });
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
        }
        if (data) {
          ioStore.setAuthToken(data.access_token);
          getSpeakerCookie().catch(err => console.error(err));
        }
      }, !authorized);
    } catch (error) {
      console.error(error);
      return unauthorized();
    }
  }
  return { getUIConfig, getAccessToken, authorize };
});

type UIConfig = {
  secure: boolean;
  spotifyEnabled: boolean;
  label: string;
};