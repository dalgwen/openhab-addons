import axios from "axios";
class OHAuthHelper {
    accessToken = ""
    authorize(setup) {
        import('pkce-challenge').then((PkceChallenge) => {
            const pkceChallenge = PkceChallenge.default()
            const authState = (setup ? 'setup-' : '') + generateUUID();
            sessionStorage.setItem('openhab.ui:codeVerifier', pkceChallenge.code_verifier)
            sessionStorage.setItem('openhab.ui:authState', authState)
            window.location = '/auth?' + urlEncodeObject({
                response_type: "code",
                client_id: window.location.origin,
                redirect_uri: window.location.origin,
                scope: "admin",
                code_challenge_method: "S256",
                code_challenge: pkceChallenge.code_challenge,
                state: authState,
            });

        })
    }
    /**
     * 
     * @param {(err: Error|null, { access_token: string, refresh_token: string }) => void} callback 
     * @param {boolean} noRefresh 
     */
    async refreshAccessToken(callback, noRefresh = false) {
        try {
            const refreshToken = this.getRefreshToken();
            if (!refreshToken) {
                throw new Error("Missing refresh token");
            }
            const payload = urlEncodeObject({
                grant_type: "refresh_token",
                client_id: window.location.origin,
                redirect_uri: window.location.origin,
                refresh_token: refreshToken,
            });
            const resp = await axios.post("/rest/auth/token", payload, {
                headers: {
                    "content-type": "application/x-www-form-urlencoded",
                    accept: "application/json",
                },
            });
            this.accessToken = resp.data.access_token;
            if (callback) {
                callback(null, resp.data);
                if (!noRefresh) {
                    if (this.refreshAccessTokenTimeoutRef) {
                        clearTimeout(this.refreshAccessTokenTimeoutRef);
                    }
                    const newRefreshFn = () => this.refreshAccessToken(callback, true);
                    this.refreshAccessTokenTimeoutRef = setTimeout(
                        newRefreshFn,
                        resp.data.expires_in * 950,
                    );
                    this.currentTokenExpireTime = new Date().getTime() + resp.expires_in * 950;
                    if (this.refreshOnVisibilityChangeFn) {
                        document.removeEventListener("visibilitychange", this.refreshOnVisibilityChangeFn);
                    }
                    this.refreshOnVisibilityChangeFn = () => {
                        if (!document.hidden && this.currentTokenExpireTime && this.currentTokenExpireTime < new Date().getTime()) {
                            console.log('Refreshing expired token')
                            this.refreshAccessToken(callback, true);
                        }
                    };
                    document.addEventListener("visibilitychange", this.refreshOnVisibilityChangeFn);
                }
            }
            return resp.data;
        } catch (error) {
            console.error(error);
            if (callback) {
                callback(error, null);
            }
        }
    }
    getAccessToken() {
        return this.accessToken;
    }
    clearAccessToken() {
        this.accessToken = "";
    }
    getRefreshToken() {
        return localStorage.getItem("openhab.ui:refreshToken") || null;
    }
    setRefreshToken(refreshToken) {
        localStorage.setItem("openhab.ui:refreshToken", refreshToken);
    }
    async tryExchangeAuthorizationCode() {
        const { code, state } = getQueryParams();
        if (code && state) {
            const authState = sessionStorage.getItem('openhab.ui:authState')
            sessionStorage.removeItem('openhab.ui:authState');
            if (authState !== state) {
                reject(new Error('Invalid state'));
            }
            if (window.history) {
                window.history.replaceState(null, window.title, window.location.href.replace('?code=' + code, '').replace('&state=' + authState, ''))
            }
            const codeVerifier = sessionStorage.getItem('openhab.ui:codeVerifier')
            sessionStorage.removeItem('openhab.ui:codeVerifier')
            const payload = urlEncodeObject({
                'grant_type': 'authorization_code',
                'client_id': window.location.origin,
                'redirect_uri': window.location.origin,
                'code': code,
                'code_verifier': codeVerifier
            });
            this.clearAccessToken();
            const resp = await axios.post('/rest/auth/token?useCookie=true', payload, {
                headers: {
                    "content-type": "application/x-www-form-urlencoded",
                    accept: "application/json",
                },
            });
            this.setRefreshToken(resp.data.refresh_token);
        }
    }
}
function getQueryParams() {
    const query = window.location.search.substring(1);
    return query.split('&').reduce((params, paramText) => {
        var pair = paramText.split('=');
        params[decodeURIComponent(pair[0])] = decodeURIComponent(pair[1]);
        return params;
    }, {});
}
function urlEncodeObject(payloadObj) {
    return Object.entries(payloadObj).reduce(
        (text, [key, value]) => {
            if (text.length)
                text = `${text}&`;
            text = `${text}${encodeURIComponent(key)}=${encodeURIComponent(
                value
            )}`;
            return text;
        },
        ""
    );
}
function generateUUID(mask = null) {
    const uuidMask = mask ? mask : 'xxxxxxxxxx';
    let
        d = new Date().getTime(),
        d2 = (performance && performance.now && (performance.now() * 1000)) || 0;
    return uuidMask.replace(/[xy]/g, c => {
        let r = Math.random() * 16;
        if (d > 0) {
            r = (d + r) % 16 | 0;
            d = Math.floor(d / 16);
        } else {
            r = (d2 + r) % 16 | 0;
            d2 = Math.floor(d2 / 16);
        }
        return (c == 'x' ? r : (r & 0x7 | 0x8)).toString(16);
    });
};
export const ohAuthHelper = new OHAuthHelper();