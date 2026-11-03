# Contributing

Thanks for helping. The most useful contribution is a **hardware report**: which players and mixers you tested, with firmware
versions, and what worked.

## Reporting problems

Use the issue templates:

- **Hardware report:** models, firmware, what you see in the app, and what the player shows.
- **Bug report:** what you did, what you expected, what happened. Please attach the log of the app if you can.

Do not post private data (your music library, serial numbers). Demo mode makes safe screenshots.

## Rules of the project

- LJ Link is **passive**. A change must never send sync, master, tempo or fader commands to players or mixers.
- No telemetry and no cloud services. Everything runs locally.
- No proprietary assets of AlphaTheta / Pioneer DJ, inMusic / Denon DJ or others (logos, fonts, artwork).
- Check beat-link's behaviour in its sources or Javadoc instead of guessing; note assumptions in `docs/decisions.md`.

## Development

JDK 21 is required.

```bash
./gradlew build          # compile and test
./gradlew installDist    # run with build/install/ljlinkpro/bin/ljlinkpro
```

Start with `--no-browser --no-tray --port 8095` for development and use the **Demo** button to work without hardware.
The interface lives in `src/main/resources/web` (plain HTML, CSS and JavaScript, no build step). Code, comments, commits and
issues are in English; the user interface and the guide are in German.

Open a pull request against `main`. Small, focused changes with a short description are easiest to review.
