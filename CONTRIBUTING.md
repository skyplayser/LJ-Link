# Contributing

Thanks for helping. The most useful contribution is a **hardware report**: which players and mixers you used, with firmware
versions, and what worked.

## Reporting problems

Use the issue templates:

- **Hardware report:** models, firmware, what you see in the app, and what the player shows.
- **Bug report:** what you did, what you expected, what happened. Please attach the log of the app if you can.

Do not post private data (your music library, serial numbers). Demo mode makes safe screenshots.

## Rules of the project

- LJ Link is **passive**. A change must never send sync, master, tempo or fader commands to players or mixers. The only
  exception is the optional Ableton Link output (off by default), which leads the tempo of a Link session.
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

## Developer Certificate of Origin

By contributing you certify that you wrote the contribution or otherwise have the right to submit it under the
project's license (Eclipse Public License 2.0), as described in the Developer Certificate of Origin 1.1
(https://developercertificate.org). Please sign off every commit with `git commit -s`, which adds the line
`Signed-off-by: Your Name <email>`. Your GitHub username and noreply address are fine.
