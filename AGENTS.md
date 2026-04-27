# AI Coding Agent Guide

This guide provides essential information for AI coding agents to be productive in this workspace.

## Build and Test Commands

| Command                              | Description                     |
|--------------------------------------|---------------------------------|
| `npm run help`                       | View root task descriptions     |
| `npm run dev:core`                   | Start API + Web simultaneously  |
| `npm run build:core`                 | Build API + Web                 |
| `npm run dev:api`                    | Start API only                  |
| `npm run test:api`                   | Run API tests                   |
| `npm run build:api`                  | Build API                       |
| `npm run dev:web`                    | Start Web only                  |
| `npm run test:web`                   | Run Web unit tests              |
| `npm run lint:web`                   | Lint and fix Web code           |
| `npm run build:web`                  | Build Web                       |
| `npm run dev:desktop:macos`          | Start macOS desktop app         |
| `npm run dev:desktop:windows`        | Start Windows desktop app       |
| `npm run build:desktop:macos`        | Build macOS desktop app         |
| `npm run build:desktop:windows`      | Build Windows desktop app       |
| `npm run dev:mobile:android`         | Build Android Debug package     |
| `npm run build:mobile:android:release` | Build Android Release package  |
| `npm run dev:demo:oauth`             | Start OAuth localhost demo      |
| `npm run dev:demo:oidc`              | Start OIDC localhost demo       |
| `npm run dev:demo:sspu`              | Start SSPU OAuth demo           |

## Project Structure

- **api/**: Backend API implementation.
- **web/**: Frontend web application.
- **desktop/**: Flutter-based desktop application.
- **mobile/**: Mobile application for Android.
- **demos/**: Demo projects for OAuth and OIDC.

## Environment Configuration

### API
- Environment directory: `api/`
- Environment variable: `KSUSER_ENV`
- Default port: `8000`

### Web
- Environment files: `web/.env.development`, `web/.env.production`
- Default API base URL: `http://localhost:8000`

### Desktop
- Environment files: `desktop/.env.development`, `desktop/.env.production`
- Key variables:
  - `FLUTTER_API_BASE_URL`
  - `FLUTTER_PASSKEY_ORIGIN`
  - `FLUTTER_OIDC_CLIENT_ID`

### Mobile Android
- Optional environment files: `mobile/android/.env.development`, `mobile/android/.env.production`
- Supported variables:
  - `API_BASE_URL`
  - `PASSKEY_RP_ID`
  - `PASSKEY_ORIGIN_HINT`
  - `APP_ENV`
  - `ENABLE_HTTP_LOGGING`

## CI / Build Workflows

GitHub Actions workflows are modularized by component:

- `.github/workflows/build-api.yml`
- `.github/workflows/build-web.yml`
- `.github/workflows/build-desktop.yml`
- `.github/workflows/build-mobile-android.yml`

These workflows trigger on relevant directory changes or can be manually triggered.

## Documentation Links

- [API Documentation](./api/README.md)
- [API Detailed Index](./api/docs/README.md)
- [Web Documentation](./web/README.md)
- [Desktop Documentation](./desktop/README.md)
- [Demo Index](./demos/README.md)