import { defineStore } from "pinia";
import axios from "axios";
import { useAssistantStore } from "./assistant";
import { OHAuthHelper } from "../utils/openhab-auth-helper";
export const useAuthStore = defineStore("auth", () => {
  const { renewToken } = useAssistantStore();
  const ohAuthHelper = new OHAuthHelper({ path: '/habspeaker' });
  function getAccessToken() {
    return ohAuthHelper.getAccessToken();
  }
  async function isTokenRequired() {
    let requireToken = true;
    // determine whether the token is required for user operations
    await axios
      .get("/rest/habspeaker/config")
      .then((resp) => {
        requireToken = resp.data.secure;
      })
      .catch((err) => {
        if (err === "Unauthorized" || err === 401) requireToken = true;
        return Promise.resolve();
      });
    return requireToken;
  }
  async function getSpeakerCookie() {
    const headers = {
      "content-type": "application/x-www-form-urlencoded",
      accept: "application/json",
    }
    if (ohAuthHelper.hasAccessToken()) { headers["Authorization"] = "Bearer " + ohAuthHelper.getAccessToken(); }
    await axios
      .post("/rest/habspeaker/cookie", `${encodeURIComponent("refresh_token")}=${encodeURIComponent(ohAuthHelper.getRefreshToken())}`, { headers });
  }
  function unauthorized() {
    console.debug("Unauthorized, redirecting to login");
    if (!import.meta.env.DEV) {
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
        renewToken(data.access_token);
        getSpeakerCookie().catch(err => console.error(err));
      }, !authorized);
    } catch (error) {
      console.error(error);
      return unauthorized();
    }
  }
  return { isTokenRequired, getAccessToken, authorize };
});
