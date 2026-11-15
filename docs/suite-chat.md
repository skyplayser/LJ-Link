# Suite chat, version 1 (Alpha)

A chat room for every program of the suite (LJ Link, LJL Link, VJ-Link and further suite programs) in the same
network, **without a host**: whoever is in the network with a suite program is in the room. Each program can switch the
chat off; then it neither sends nor listens.

## Transport

- UDP multicast, group **239.255.76.76**, port **47676** (administratively scoped, stays in the local network).
  Time to live 1 (never routed). Sent on every IPv4 network adapter that is up (not loopback, not 169.254.x.x),
  and received on all of them. Multicast loopback on: two programs on the same computer see each other.
- Sockets bind `0.0.0.0:47676` with address and port reuse, so several suite programs on one computer can listen.
- One JSON object per datagram, UTF-8, at most 8 KB. Unknown fields and types are ignored.

## Messages

Every datagram has `suite: "chat"`, `v: 1`, `t` (type) and `peer` (random id of the sending program, new on every
start, 8–32 characters `[0-9a-z]`). Datagrams from one's own `peer` are ignored.

```json
{"suite":"chat","v":1,"t":"here","peer":"k3j9x2m7p0","nick":"Phil","where":"FOH","app":"LJ Link"}
{"suite":"chat","v":1,"t":"msg","peer":"k3j9x2m7p0","id":"k3j9x2m7p0-17","nick":"Phil","where":"FOH","app":"LJ Link","text":"Drop in 30 s!","at":1794685866935}
{"suite":"chat","v":1,"t":"msg","peer":"q8w2e4r5t6","id":"q8w2e4r5t6-3","nick":"DJ David","where":"DJ Booth","app":"LJL Link","text":"Lichtpult startet neu – 2 Minuten!","urgent":true,"at":1794685870000}
{"suite":"chat","v":1,"t":"history?","peer":"a8f0q1z9y8"}
{"suite":"chat","v":1,"t":"bye","peer":"k3j9x2m7p0"}
```

- `here`: presence, sent on start (and when the nickname changes) and every **5 s**. A peer not heard of for **15 s**
  has left. A program without a nickname yet sends `nick: ""` (it is in the room, but has not written yet).
- `nick` / `where`: the person's name ("DJ David") and where they stand ("DJ Booth", "FOH", "Bar"); both asked when
  entering the chat (fields "Name – What is your name?" and "Position – Where are you?"). `where` may be empty.
- `msg`: a message. `id` = `peer` + "-" + a counter + **8 random characters** `[0-9a-z]` (e.g. `k3j9x2m7p0-17q8w2e4r5`),
  at most 64 characters; nobody can then take a program's next ids in advance. A receiver drops a `msg` whose `id`
  does not start with its `peer` + "-". Every message is sent **twice** (at once and 300 ms later), receivers show an
  `id` only once. In a history answer `peer` is the **author's** peer: everything in `id` before the first "-".
  `at`: the sender's clock in ms since 1970; a receiver takes it only as a finite number from 0 to 4102444800000 (the
  year 2100), otherwise its own clock. A **live** message is placed (and shown with the time) by its **arrival** at the
  receiver – a sender's wrong clock must not hide it; only history uses the `at` it carries.
- **Strict types**: `peer`, `id`, `nick`, `where`, `app`, `text` are strings, `v` and `at` numbers, `urgent` and `old`
  booleans. A field of another type counts as missing (`urgent: 1` or `"true"` is no EMERGENCY, `text: 42` is no text).
- **Trim** means: Unicode white space removed at both ends; spaces inside are kept as they are.
- `history?`: sent once after joining, and **once more 12 s later if no history came** (two newcomers within 10 s:
  the others' lock leaves the second one without history otherwise). Every peer answers by sending its last up to 20 messages again (as `msg` with
  `old: true`, 20 ms apart); receivers drop the ones they have (same `id`) and sort by `at`. For 5 s after its own
  `history?` a receiver does not count `old` messages towards the limit per peer.
  A `msg` with `old: true` never adds a peer to the room nor keeps one in it (it may come from someone who left long
  ago; otherwise everyone who ever wrote would stand "in the room" again for 15 s after every newcomer). Outside its
  own 5-s window a receiver takes at most 10 such messages in 10 s from senders that are not in the room, together.
  A program answers `history?` **at most once in 10 s, whoever asks** (one lock for all askers; whoever asks within
  these 10 s gets nothing from this program, the answers of the others are enough), and **not at all when it has no
  messages**. Otherwise one small datagram would make every program in the room send 20 messages each time.
- `urgent: true`: an **EMERGENCY** message (its own button next to "Send"). Every other program shows it at once as a
  pop-up at the bottom right of the screen with a **yellow blinking border**, with name, position, program and text,
  until it is closed – also when the chat window is closed. A history answer never marks a message urgent again
  (`old` messages are never shown as a pop-up). At most **3 EMERGENCY messages per peer in 60 s** and **20 open
  pop-ups** in all; more are shown as normal messages, without a pop-up. With more than one open the pop-up has
  "Close all". Switching the chat off closes every pop-up. The word stays "EMERGENCY" in every language.
  EMERGENCY is **no alarm or safety system**: no substitute for emergency calls, fire alarms, radios or security
  staff. Datagrams can be lost or late, networks can block multicast, and senders are not verified.
  A pop-up **never takes the keyboard focus** (whoever is typing must not confirm it unread with Space or Enter). When
  the program's window is in the background or minimized, the program draws attention without sound (Dock bounce,
  flashing taskbar button).
- `bye`: the program leaves (chat switched off or quitting). Switching off also forgets every message in memory;
  switched on again, the history comes from the others.
- A program that comes back (switched on again, restarted) gets the history from the others, but not the messages it
  wrote itself before – they are forgotten with the rest; nobody else sends them back as its own. That is intended.
- A history answer may carry `urgent: true` together with `old: true`; that is never a pop-up and never blinks.
- Unread: only live messages of others count – never history (`old`), never one's own.

## Limits (every receiver enforces them)

- `nick` and `where`: at most 24 characters each, `text`: at most 500 characters (Unicode code points). Line breaks, tabs and the
  line/paragraph separators U+2028/2029 become spaces, control characters and direction marks (U+061C, U+200E/F,
  U+202A–202E, U+2066–2069) are removed, then trimmed.
  An empty `text` is dropped; an empty `nick` is shown as the program's name.
- At most 10 messages per peer in 10 s (more are dropped), at most 200 messages kept, at most 50 peers.
- At most 64 datagrams waiting to be sent; more are dropped (a flood never fills the memory).
- `bye` on quitting is sent at once, not queued.
- Text only: never interpret it as HTML or Markdown.
- Test instances that are not meant for a live test use another port, too (LJ Link: `-Dljlink.chatPort=…` or
  `LJLINK_CHAT_PORT`; VJ-Link: `VJLINK_CHAT_PORT`).
- Automatic tests never use port 47676 (they would write into a real room): VJ-Link test instances 47677 and its network unit
  tests 47678, LJL Link self test 47699; LJ Link's unit tests do not open a socket.

## Display (every program)

- Unread normal messages: a circle in the accent colour with their number (LJ Link and VJ-Link at their chat button,
  LJL Link on its Dock icon and with a speech bubble in the menu bar).
- Unread EMERGENCY messages (an `urgent` message from someone else that came while the chat was not open): the chat
  button **blinks yellow** (#FFD60A, about once a second, like the pop-up border) until the chat is opened – in LJ Link
  and VJ-Link (developer's request). Confirming the pop-up with "OK" does not stop it; opening the chat does. With
  "reduce motion" switched on in the system: steady yellow instead of blinking.
- Names are shown as "name (position) · program". No sound by default (the computer's output may be the mixer).

## Privacy

- Chat text is never written to a log, a file or a problem report; a problem report says only whether the chat is on
  and how many messages are in memory.
- Every device in the same network can read along, anyone there with a suite program can write (like a walkie-talkie
  channel); nothing is encrypted. There is no
  authentication: anyone in the network can send datagrams with another program's `peer` (a fake `bye`, using up its
  message limit). The random part of `id` only keeps others from blocking a program's next messages in advance. Networks that block
  multicast (guest Wi-Fi with client isolation) have no chat.
- macOS asks for "Local Network" access the first time.

## Program notes

- **LJ Link**: the program itself is the peer (one nickname, kept in the settings). Only the browser on the LJ Link
  computer reads and writes the chat (local API `GET/POST /api/chat`, `POST /api/chat/settings`, `/api/chat/ack`,
  `/api/chat/read`), not a tablet. Setting "Suite chat" on by default. An EMERGENCY also opens a window of its own at
  the bottom right (always on top, without taking the keyboard focus), because LJ Link often runs only as the program
  icon; "OK" there or in the browser confirms it in both. The program icon shows the unread number and has "Open suite
  chat".
- **LJL Link**: on by default, switch in the app. Asks for name and position when the DJ opens the chat; keeps them
  only in memory (nothing is written to the DJ's Mac). EMERGENCY: a window of its own at the bottom right, above every
  window (also Serato in full screen), never taking the keyboard from Serato. Unread: number on the Dock icon, speech
  bubble in the menu bar. Never a sound.
- **VJ-Link**: same protocol (Alpha). Chat and pop-ups only in the control window, never in an output window on a
  projector. Name and position kept in `chat.json`, texts only in memory.
- **Further suite programs**: same protocol.
- Changes to this document are agreed between the maintainers of the suite programs.
