# Manuscript Watch 0.1.8 — Simple tracking setup

- Removed the technical **Status CSS selector** entry field from Add/Edit manuscript.
- Changed **Sign in** on manuscript cards to **Open journal** and **Use page** to **Track page**.
- Clarified the three-step setup and simplified failure messages.
- Automatic status detection remains in place for both Springer detail pages and general/Elsevier manuscript tables.
- Existing stored CSS selector rules are preserved internally for backward compatibility, but new manuscripts use automatic detection.
- No feature removal to the actual website monitoring, history, ORCID vault or notifications.

## Set up without HTML

1. Add a paper with title, journal, manuscript ID and the journal HTTPS sign-in URL.
2. Tap **Open journal**, sign in, and browse to the manuscript's status page.
3. Tap **Track page** once the current status is visible. The app saves its tracking link.

The app cannot yet guarantee detection for every publisher layout, especially dynamic authenticated pages. It reports when no clear status was found; it does not guess.
