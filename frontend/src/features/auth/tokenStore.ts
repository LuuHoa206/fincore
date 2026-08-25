const REFRESH_TOKEN_KEY = 'fincore.refresh-token'

let accessToken: string | null = null

export const tokenStore = {
  getAccessToken: () => accessToken,
  getRefreshToken: () => window.localStorage.getItem(REFRESH_TOKEN_KEY),
  setTokens: (nextAccessToken: string, nextRefreshToken: string) => {
    accessToken = nextAccessToken
    window.localStorage.setItem(REFRESH_TOKEN_KEY, nextRefreshToken)
  },
  clear: () => {
    accessToken = null
    window.localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}
