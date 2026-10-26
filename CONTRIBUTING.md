# Contributing to Manuscript Watch

Contributions are welcome, especially test cases and improvements that make status recognition more reliable without weakening privacy or publisher access controls.

## Before contributing

- Use **US English** in documentation, source comments, issue titles, and user-facing text.
- Follow the existing Kotlin/Android project structure and avoid unnecessary dependencies.
- Respect journal terms of service. Do not introduce CAPTCHA bypasses, credential harvesting, or unauthorized authentication automation.
- Do not upload real manuscripts, private tracking URLs, journal session cookies, ORCID credentials, tokens, or signing keys.
- Use fictional publisher names and synthetic manuscript IDs in tests and screenshots unless the content is genuinely public and appropriate to share.

## Development

Use Android Studio with JDK 17 and Android SDK 35, or the provided GitHub Actions build workflow.

Run:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

On Windows, the repository includes a `gradlew.bat` bootstrap script. Do not commit generated build output.

## Pull requests

Summarize the change, explain the affected publisher/login flow, and include or update synthetic regression tests. State clearly which behavior was tested on a real Android device and which remains unverified. Avoid claiming universal publisher support from isolated examples.

For confidential or security-related findings, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
