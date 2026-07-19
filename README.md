# browser

A lightweight private Android browser.

## Privacy behavior

- Does not persist browser history.
- Clears WebView cache, cookies, form data, and web storage.
- Blocks file access, content access, geolocation prompts, popups, downloads, and non-http(s) URLs.
- Finishes the app immediately when the screen turns off.
- Uses `FLAG_SECURE` to block screenshots and screen recording in the app window.

## Build with GitHub Actions

1. Push this folder to a GitHub repository.
2. Open the repository's **Actions** tab.
3. Run **Build Android APK** or push to `main`.
4. Download the `safe-browser-apk` artifact.

With GitHub CLI:

```bash
gh run download --name safe-browser-apk
```

The debug APK will be inside the downloaded artifact folder.
