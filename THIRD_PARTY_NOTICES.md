# Third party notices

LJ Link is © 2026 Philipp Spruck / NEXTPHILM and is distributed under the
[Eclipse Public License 2.0](LICENSE). Its source code is available at https://github.com/skyplayser/LJ-Link.

The installers contain the open source components below, unchanged. Each one is distributed under its own license. The
license texts are included in the program (menu → *Licenses*) and in `src/main/resources/web/legal/`; the license and notice
files that the libraries ship themselves are copied into the program by the build (`legal/libraries/`). The build fails
when a library that ships with the program is missing from this table (`checkThirdPartyNotices`).

| Component | Version | Purpose | License | Source | Maven artifacts |
|---|---|---|---|---|---|
| beat-link, Copyright © Deep Symmetry, LLC | 8.0.0 | Pro DJ Link protocol | EPL-2.0 ¹ | https://github.com/Deep-Symmetry/beat-link | `org.deepsymmetry:beat-link` 8.0.0 |
| crate-digger, Copyright © Deep Symmetry, LLC | 0.2.1 | rekordbox database access | EPL-2.0 ¹ | https://github.com/Deep-Symmetry/crate-digger | `org.deepsymmetry:crate-digger` 0.2.1 |
| electro, Copyright © Deep Symmetry, LLC | 0.1.4 | beat-link helpers | EPL-2.0 ¹ | https://github.com/Deep-Symmetry/electro | `org.deepsymmetry:electro` 0.1.4 |
| Remote Tea ONC/RPC | 1.1.4 | NFS access to the players (used by crate-digger) | LGPL-2.0 ² | https://sourceforge.net/projects/remotetea/ | `org.acplt.remotetea:remotetea-oncrpc` 1.1.4 |
| Kaitai Struct Java runtime | 0.10 | parsing rekordbox files | MIT | https://github.com/kaitai-io/kaitai_struct_java_runtime | `io.kaitai:kaitai-struct-runtime` 0.10 |
| JmDNS | 3.6.1 | Bonjour / mDNS announcement in the network | Apache-2.0 | https://github.com/jmdns/jmdns | `org.jmdns:jmdns` 3.6.1 |
| Javalin | 6.7.0 | local web server | Apache-2.0 | https://github.com/javalin/javalin | `io.javalin:javalin` 6.7.0 |
| Eclipse Jetty (server, servlet, websocket …) | 11.0.25 | web server and WebSocket | EPL-2.0 or Apache-2.0 | https://github.com/jetty/jetty.project | `org.eclipse.jetty:jetty-http` `org.eclipse.jetty:jetty-io` `org.eclipse.jetty:jetty-security` `org.eclipse.jetty:jetty-server` `org.eclipse.jetty:jetty-servlet` `org.eclipse.jetty:jetty-util` `org.eclipse.jetty:jetty-webapp` `org.eclipse.jetty:jetty-xml` `org.eclipse.jetty.websocket:websocket-core-common` `org.eclipse.jetty.websocket:websocket-core-server` `org.eclipse.jetty.websocket:websocket-jetty-api` `org.eclipse.jetty.websocket:websocket-jetty-common` `org.eclipse.jetty.websocket:websocket-jetty-server` `org.eclipse.jetty.websocket:websocket-servlet` 11.0.25 |
| Jetty Jakarta Servlet API | 5.0.2 | servlet API | EPL-2.0 or GPL-2.0 with Classpath Exception | https://github.com/jetty/jetty.project | `org.eclipse.jetty.toolchain:jetty-jakarta-servlet-api` 5.0.2 |
| Jackson (core, databind, annotations) | 2.22.4 (annotations 2.22) | JSON | Apache-2.0 | https://github.com/FasterXML/jackson | `com.fasterxml.jackson.core:jackson-core` `com.fasterxml.jackson.core:jackson-databind` `com.fasterxml.jackson.core:jackson-annotations` 2.22.4, 2.22 |
| Kotlin standard library | 1.9.25 | needed by Javalin | Apache-2.0 | https://github.com/JetBrains/kotlin | `org.jetbrains.kotlin:kotlin-stdlib` `org.jetbrains.kotlin:kotlin-stdlib-jdk7` `org.jetbrains.kotlin:kotlin-stdlib-jdk8` 1.9.25 |
| JetBrains annotations | 13.0 | needed by Kotlin | Apache-2.0 | https://github.com/JetBrains/java-annotations | `org.jetbrains:annotations` 13.0 |
| SLF4J (api, simple) | 2.0.17 | logging | MIT | https://github.com/qos-ch/slf4j | `org.slf4j:slf4j-api` `org.slf4j:slf4j-simple` 2.0.17 |
| SQLite JDBC with encryption (sqlite-jdbc-crypt by Willena) | 3.49.0.0 | reading the Denon Engine library | Apache-2.0; contains code under the BSD license (© 2006 David Crawshaw, file `LICENSE.zentus`) and SQLite3MultipleCiphers (MIT, Ulrich Telle) ³; SQLite itself is public domain | https://github.com/Willena/sqlite-jdbc-crypt | `io.github.willena:sqlite-jdbc` 3.49.0.0 |
| Apache Commons Math | 3.6.1 | math | Apache-2.0 | https://commons.apache.org/proper/commons-math/ | `org.apache.commons:commons-math3` 3.6.1 |
| API Guardian | 1.1.2 | annotations | Apache-2.0 | https://github.com/apiguardian-team/apiguardian | `org.apiguardian:apiguardian-api` 1.1.2 |
| Eclipse Temurin (OpenJDK runtime, in the installers only) | 21 (exact version: file `release` in the bundled runtime) | Java runtime | GPL-2.0 with Classpath Exception ² | https://adoptium.net, https://github.com/adoptium | – |

¹ The Maven metadata of these versions still names EPL-1.0; the projects themselves are distributed under EPL-2.0.
² See the source code offer below.
³ The jar does not contain the license of SQLite3MultipleCiphers (https://github.com/utelle/SQLite3MultipleCiphers); its MIT
license text is included in the program as `legal/MIT-sqlite3mc.txt`, the BSD text of David Crawshaw as `legal/BSD-zentus.txt`.

The source code of every component is available at the address in the table. Remote Tea is used as an unchanged, separate
library (`remotetea-oncrpc-1.1.4.jar` in the program folder); under the LGPL you may replace it with a modified version.
The OpenJDK runtime keeps its own legal notices in the `legal` folder of the bundled runtime.

## Logo font

The letters "LJ" in the LJ Link logo (`logo.svg` and the installer icons) are outlines of the font Inter Display
ExtraBold, Copyright © 2016 The Inter Project Authors (https://github.com/rsms/inter), licensed under the SIL Open Font
License 1.1. The font itself is not included in the program, only the drawn outlines of these two letters. The license text
is included as `legal/OFL-Inter.txt`.

## Source code offer

The installers contain the OpenJDK runtime of Eclipse Temurin (GPL-2.0 with Classpath Exception) and Remote Tea
(LGPL-2.0). The complete corresponding source code of the exact versions is attached to each release (`OpenJDK21U-jdk-sources_<version>.tar.gz` and
`remotetea-oncrpc-1.1.4-sources.jar`). For at least three years after each release we will also send it to anyone on request; write to
anfrage@nextphilm.de.

## Protocol knowledge

The Denon StageLinQ protocol was learned from the open source libraries [go-stagelinq](https://github.com/icedream/go-stagelinq)
(icedream) and [StageLinq](https://github.com/chrisle/StageLinq) (chrisle), both MIT licensed. The format of the Engine
library (`m.db`) follows the documentation of the open source project [libdjinterop](https://github.com/xsco/libdjinterop)
(LGPL), used as format documentation only. Pro DJ Link protocol knowledge comes from the
[dysentery](https://github.com/Deep-Symmetry/dysentery) analysis by Deep Symmetry, the Ableton Link discovery format from
Ableton's open source Link library, and the Serato tags from public documentation. No code was copied from them.

## Trademarks

LJ Link is an independent project. It is not an official product of, and is not endorsed by, any maker of DJ, lighting or music hardware or software. All product names and trademarks mentioned in LJ Link and its documentation belong to their respective owners and are used only to describe compatibility. Ableton and
Link are trademarks of Ableton AG. LJ Link is not related to the LightJockey software by Martin.
