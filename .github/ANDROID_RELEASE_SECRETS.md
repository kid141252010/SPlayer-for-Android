# Android Release Secrets

GitHub Actions `Android Release` workflow supports signed `release APK` builds.

Required repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## How to prepare `ANDROID_KEYSTORE_BASE64`

Convert your keystore file to base64, then store the full output in the secret.

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\release.keystore"))
```

macOS / Linux:

```bash
base64 -w 0 /path/to/release.keystore
```

## Workflow behavior

- `build_type = release`
  - requires all signing secrets
  - builds `app-release.apk`
  - signs with your keystore
- `build_type = debug`
  - does not require signing secrets
  - builds `app-debug.apk`
