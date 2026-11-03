# Third party notices

LJ Link is © 2026 Philipp Spruck / NEXTPHILM and is distributed under the
[Eclipse Public License 2.0](LICENSE). Its source code is available at https://github.com/skyplayser/LJ-Link.

The installers contain the open source components below, unchanged. Each one is distributed under its own license. The
license texts are included in the program (menu → *Licenses*) and in `src/main/resources/web/legal/`; the license and notice
files that the libraries ship themselves are copied into the program by the build (`legal/libraries/`).

| Component | Version | Purpose | License | Source |
|---|---|---|---|---|
| beat-link (Deep Symmetry) | 8.0.0 | Pro DJ Link protocol | EPL-2.0 ¹ | https://github.com/Deep-Symmetry/beat-link |
| crate-digger (Deep Symmetry) | 0.2.1 | rekordbox database access | EPL-2.0 ¹ | https://github.com/Deep-Symmetry/crate-digger |
| electro (Deep Symmetry) | 0.1.4 | beat-link helpers | EPL-2.0 ¹ | https://github.com/Deep-Symmetry/electro |
| Remote Tea ONC/RPC | 1.1.4 | NFS access to the players (used by crate-digger) | LGPL-2.0 | https://sourceforge.net/projects/remotetea/ |
| Kaitai Struct Java runtime | 0.10 | parsing rekordbox files | MIT | https://github.com/kaitai-io/kaitai_struct_java_runtime |
| Javalin | 6.7.0 | local web server | Apache-2.0 | https://github.com/javalin/javalin |
| Eclipse Jetty (server, servlet, websocket …) | 11.0.25 | web server and WebSocket | EPL-2.0 or Apache-2.0 | https://github.com/jetty/jetty.project |
| Jetty Jakarta Servlet API | 5.0.2 | servlet API | EPL-2.0 or GPL-2.0 with Classpath Exception | https://github.com/jetty/jetty.project |
| Jackson (core, databind, annotations) | 2.22 | JSON | Apache-2.0 | https://github.com/FasterXML/jackson |
| Kotlin standard library | 1.9.25 | needed by Javalin | Apache-2.0 | https://github.com/JetBrains/kotlin |
| JetBrains annotations | 13.0 | needed by Kotlin | Apache-2.0 | https://github.com/JetBrains/java-annotations |
| SLF4J (api, simple) | 2.0.17 | logging | MIT | https://github.com/qos-ch/slf4j |
| SQLite JDBC | 3.49.0.0 | database access | Apache-2.0 (SQLite itself: public domain) | https://github.com/xerial/sqlite-jdbc |
| Apache Commons Math | 3.6.1 | math | Apache-2.0 | https://commons.apache.org/proper/commons-math/ |
| API Guardian | 1.1.2 | annotations | Apache-2.0 | https://github.com/apiguardian-team/apiguardian |
| OpenJDK runtime (in the installers) | 21 | Java runtime | GPL-2.0 with Classpath Exception | https://openjdk.org |

¹ The Maven metadata of these versions still names EPL-1.0; the projects themselves are distributed under EPL-2.0.

The source code of every component is available at the address in the table. Remote Tea is used as an unchanged, separate
library (`remotetea-oncrpc-1.1.4.jar` in the program folder); under the LGPL you may replace it with a modified version.
The OpenJDK runtime keeps its own legal notices in the `legal` folder of the bundled runtime.

The Denon StageLinQ protocol was learned from the open source libraries [go-stagelinq](https://github.com/icedream/go-stagelinq)
(icedream) and [StageLinq](https://github.com/chrisle/StageLinq) (chrisle), both MIT licensed. Pro DJ Link protocol knowledge
comes from the [dysentery](https://github.com/Deep-Symmetry/dysentery) analysis by Deep Symmetry, the Ableton Link discovery
format from Ableton's open source Link library, and the Serato tags from public documentation. No code was copied from them.

## Trademarks

LJ Link is not an official product of, and is not endorsed by, AlphaTheta / Pioneer DJ, inMusic / Denon DJ, MA Lighting,
Daslight, Serato, Ableton or Native Instruments. Pioneer DJ, Pro DJ Link, rekordbox, CDJ, DJM, XDJ, Denon DJ, Engine DJ,
StageLinQ, grandMA, dot2, Daslight, Serato, Ableton, Ableton Link and Traktor are trademarks of their owners and are named
only to describe compatibility. LJ Link is not related to the LightJockey software by Martin.
