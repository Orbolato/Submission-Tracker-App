# Security Policy

## Status

Manuscript Watch is experimental software. It handles authenticated publisher WebView sessions and offers optional local storage of ORCID login credentials. It has not undergone an independent security assessment and is not recommended for users who require production-grade credential management.

## Reporting a vulnerability

**Do not share sensitive details in a public GitHub issue.** In particular, never submit passwords, tokens, cookies, manuscript URLs, private paper text, or other confidential editorial information.

If private vulnerability reporting is enabled for this repository, use GitHub's **Security → Report a vulnerability** feature. Otherwise, contact the repository maintainer through GitHub without including secret information, and request a private reporting channel.

Please include the affected app version and a minimal reproduction using synthetic data where possible.

## Security expectations

- The ORCID credential vault is optional. User-triggered filling is restricted to verified HTTPS ORCID hosts. There is no unattended password submission.
- The app relies on publishers' permitted login processes and does not bypass access controls, CAPTCHA, or multifactor authentication.
- Debug APKs produced by GitHub Actions are development artifacts signed using ephemeral or runner-specific debug keys; do not treat them as production releases.
- Do not commit signing keys, local Android data, session cookies, credentials, or actual manuscript information.

Security fixes may affect login/session compatibility, so testing with non-sensitive accounts or synthetic data is encouraged where possible.
