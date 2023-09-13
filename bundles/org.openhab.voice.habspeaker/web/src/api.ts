
import { OHOAuth } from "./oh-oauth";

export class HABSpeakerREST {
  private serverToken: string | null = null;
  private ohAuthHelper: OHOAuth;
  private tokenListener: (accessToken: string) => void = () => { };
  constructor(private speakerId: () => Promise<string | null>, private ohUrl: () => Promise<string>,) {
    this.ohAuthHelper = new OHOAuth({ path: '/habspeaker', ohUrl });
  }
  public getAccessToken() {
    return this.serverToken ?? this.ohAuthHelper.hasAccessToken() ? this.ohAuthHelper.getAccessToken() : null;
  }
  public async getUIConfig(): Promise<UIConfig> {
    const headers = {
      accept: "application/json",
    } as { [key: string]: string };
    if (this.getAccessToken()) { headers["Authorization"] = "Bearer " + this.getAccessToken(); }
    const response = await fetch(`${await this.ohUrl()}/rest/habspeaker/config/${await this.speakerId() ?? "never"}`, { headers });
    if (!response.ok) {
      if (response.status === 401) {
        throw new UnauthorizedError();
      } else {
        throw new Error(`Response failed with status ${response.status}: ${response.statusText}`);
      }
    }
    return await response.json();
  }
  public async authorize() {
    try {
      const authorized = await this.ohAuthHelper.tryExchangeAuthorizationCode();
      await this.ohAuthHelper.refreshAccessToken((err, data) => {
        if (err) {
          return this.redirectToLogin();
        } else if (data) {
          this.tokenListener(data.access_token);
        }
      }, !authorized);
    } catch (error) {
      console.error(error);
      return this.redirectToLogin();
    }
  }
  public redirectToLogin() {
    console.debug("Unauthorized, redirecting to login");
    this.ohAuthHelper.authorize();
  }
  public setTokenListener(listener: (accessToken: string) => void) {
    this.tokenListener = listener;
  }
  public setServerToken(token: string | null) {
    this.serverToken = token;
  }
}
export class UnauthorizedError extends Error { }
export type UIConfig = {
  // speaker thing label
  label: string;
  // custom sample rate for the audio system
  sampleRate?: number;
  // primary color
  primaryColor?: string;
  // primary color
  secondaryColor?: string;
  // primary color
  tertiaryColor?: string;
  // logo path
  logoUrl?: string;
};