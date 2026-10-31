# RemTeh apps on Ubuntu

User-scoped installer for Ubuntu ARM64 on Switch OLED. Run as the desktop user. Python 3.11+ and Chromium are required for web apps. No root service or sandbox-disabling flag is installed.

Download the source ZIP from [a verified Device Lab release](https://github.com/remteh-by/Device-Lab/releases), verify SHA256SUMS, and from its `ubuntu/` folder run:

```sh
python3 remteh_webapps.py install
```

The installer creates `~/.local/share/remteh-webapps`, desktop/menu launchers, one loopback-only HTTP server on 127.0.0.1:18730 and a user systemd update timer. Existing Mouse Tester origin and browser profile paths are retained. A changed updater script keeps one previous copy. Pairings, accounts and browser profiles are outside version cleanup.

Apps: Mouse Tester, Device Lab, X Play native ARM64 and an online Gamepad Tester covering DualSense / Xbox / Joy-Con through available browser API. Android APKs run under Android; this installer uses the web controller tool on Ubuntu. Deckivo has its own independently managed updater.

The hourly timer runs while the Ubuntu user session is active. It checks published stable and preview releases with numeric tags, validates the exact repository/filename, digest and size, checks ZIP paths/types, site CSP and ARM64 executable, and switches the current link atomically. Running native versions are protected from cleanup. Web updates offer a save/reload step. A failed download, low space, interrupt or failed state commit keeps the working version.

```sh
python3 ~/.local/share/remteh-webapps/remteh_webapps.py status
python3 ~/.local/share/remteh-webapps/remteh_webapps.py update all
systemctl --user status remteh-apps-update.timer
```

Rollback retains the current data/profile, switches to the previous package and holds that failed version until a newer release:

```sh
python3 ~/.local/share/remteh-webapps/remteh_webapps.py rollback xplay
```

Use `mouse` or `lab` for those packages. The web service is `remteh-mouse.service` to preserve the existing installation. Disable the timer with `systemctl --user disable --now remteh-apps-update.timer` when desired. The installer does not alter the Switch partition table, firmware, Android installation or kernel settings.

Eleven updater fault tests cover malformed archives, repository mismatch, bad digest, low space, interruption, atomic commit failure, recovery, active-version retention and rollback. Real X Play launcher and profile save were checked on Switch ARM64. Actual Xbox gameplay, audio and controller acceptance require the console/service and remain separate.
