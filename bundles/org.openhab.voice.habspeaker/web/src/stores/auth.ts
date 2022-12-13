import { defineStore } from "pinia";
import axios from "axios";
import { useAssistantStore } from "./assistant";
import { useSettingsStore } from "./settings";
import { OHAuthHelper } from "../utils/openhab-auth-helper";
export const useAuthStore = defineStore("auth", () => {
  const { renewToken } = useAssistantStore();
  const { speakerId } = useSettingsStore();
  const ohAuthHelper = new OHAuthHelper({ path: '/habspeaker' });
  function getAccessToken() {
    return ohAuthHelper.getAccessToken();
  }
  async function getUIConfig(): Promise<UIConfig> {
    try {
      return (await axios.get<UIConfig>(`/rest/habspeaker/config/${speakerId}`)).data;
    } catch (error) {
      if (error === "Unauthorized" || error === 401) {
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
      .post("/rest/habspeaker/cookie", `${encodeURIComponent("refresh_token")}=${encodeURIComponent(refreshToken)}`, { headers });
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
          renewToken(data.access_token);
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