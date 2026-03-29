/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_DEBUG_STATE: 'dev' | 'prd'
  readonly VITE_OAUTH_QQ_APPID: string
  readonly VITE_OAUTH_GITHUB_CLIENT_ID: string
  readonly VITE_OAUTH_MICROSOFT_CLIENT_ID: string
  readonly VITE_OAUTH_MICROSOFT_TENANT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
