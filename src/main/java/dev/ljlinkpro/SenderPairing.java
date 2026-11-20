package dev.ljlinkpro;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * The DJ Macs the LJ has approved for LJL Link without {@code club.json} (Alpha, sender protocol "Approval without
 * club.json"), and the Macs refused in the last {@value #DENY_MINUTES} minutes.
 * <p>
 * A Mac is known only by its pseudonym ({@code device}, 32 hex digits that LJL Link derives for this one LJ Link from
 * the booth id) and the name it gave, cut to {@value SenderClub#MAX_NAME} characters, with the first and last time it
 * was seen. Nothing else is kept, nothing from here is logged, put in a problem report or sent to a tablet: only the
 * local API ({@link ControlGuard}) shows the names, and the pseudonym never leaves this class except to the settings
 * file. The browser addresses an entry by a reference ({@code ref}) that is new with every program start.
 * <p>
 * At most {@value #MAX_APPROVED} entries; a new one pushes out the one not seen for the longest time.
 */
final class SenderPairing {
    static final String APPROVED_SETTING = "senderApprovedMacs";
    static final int MAX_APPROVED = 32;
    static final int DENY_MINUTES = 10;
    static final long DENY_NANOS = DENY_MINUTES * 60_000_000_000L;
    /** Refusals kept in memory at most; the oldest goes first (pseudonyms can be made up, so this must not grow). */
    static final int MAX_DENIED = 256;
    private static final Pattern DEVICE = Pattern.compile("[0-9a-fA-F]{32}");

    /** Where the list is kept: the settings file, or memory in tests. */
    interface Store {
        List<Entry> load();

        void save(List<Entry> entries);
    }

    /** One approved Mac. */
    static final class Entry {
        final String device;
        String name;
        final long first;
        long last;
        /** For the browser only; changes with every start. */
        String ref;

        Entry(String device, String name, long first, long last) {
            this.device = device;
            this.name = name;
            this.first = first;
            this.last = last;
        }
    }

    private final Store store;
    private final LongSupplier clock;
    /** Guarded by this, by pseudonym; null until first used (read lazily from the store). */
    private Map<String, Entry> approved;
    /** Guarded by this: pseudonym → when it was refused (System.nanoTime()). */
    private final Map<String, Long> denied = new LinkedHashMap<>();
    private int nextRef = 1;

    SenderPairing(Store store, LongSupplier clock) {
        this.store = store;
        this.clock = clock;
    }

    /** The list in the settings file, times from the wall clock. */
    static SenderPairing fromSettings() {
        return new SenderPairing(new SettingsStore(), System::currentTimeMillis);
    }

    /** A list only in memory (tests, and the constructors of {@link SenderInbox} that tests use). */
    static SenderPairing inMemory(LongSupplier clock) {
        return new SenderPairing(new Store() {
            private List<Entry> saved = List.of();

            @Override
            public List<Entry> load() {
                List<Entry> out = new ArrayList<>();
                for (Entry e : saved) {
                    out.add(new Entry(e.device, e.name, e.first, e.last));
                }
                return out;
            }

            @Override
            public void save(List<Entry> entries) {
                saved = List.copyOf(entries);
            }
        }, clock);
    }

    /**
     * The pseudonym from a hello in lowercase, or null when it is not exactly 32 hex digits (then it counts as missing).
     */
    static String device(JsonNode hello) {
        JsonNode d = hello.path("device");
        if (!d.isTextual() || !DEVICE.matcher(d.asText()).matches()) {
            return null;
        }
        return d.asText().toLowerCase(Locale.ROOT);
    }

    private Map<String, Entry> approved() {
        if (approved == null) {
            approved = new LinkedHashMap<>();
            List<Entry> loaded;
            try {
                loaded = store.load();
            } catch (RuntimeException e) {
                loaded = List.of();
            }
            for (Entry e : loaded) {
                if (e.device != null && DEVICE.matcher(e.device).matches() && approved.size() < MAX_APPROVED) {
                    e.ref = "m" + nextRef++;
                    approved.put(e.device.toLowerCase(Locale.ROOT), e);
                }
            }
        }
        return approved;
    }

    private void save() {
        store.save(new ArrayList<>(approved().values()));
    }

    synchronized boolean isApproved(String device) {
        return device != null && approved().containsKey(device);
    }

    /** An approved Mac connected again: its name and last seen are updated. */
    synchronized void seen(String device, String name) {
        Entry e = device == null ? null : approved().get(device);
        if (e == null) {
            return;
        }
        String n = SenderClub.clean(name, SenderClub.MAX_NAME);
        if (!n.isEmpty()) {
            e.name = n;
        }
        e.last = clock.getAsLong();
        save();
    }

    /** The LJ allowed this Mac; when the list is full, the entry not seen for the longest time makes room. */
    synchronized void approve(String device, String name) {
        if (device == null) {
            return;
        }
        long now = clock.getAsLong();
        Map<String, Entry> list = approved();
        Entry old = list.get(device);
        if (old != null) {
            seen(device, name);
            return;
        }
        while (list.size() >= MAX_APPROVED) {
            Entry oldest = null;
            for (Entry e : list.values()) {
                if (oldest == null || e.last < oldest.last) {
                    oldest = e;
                }
            }
            list.remove(oldest.device);
        }
        Entry e = new Entry(device, SenderClub.clean(name, SenderClub.MAX_NAME), now, now);
        e.ref = "m" + nextRef++;
        list.put(device, e);
        denied.remove(device);
        save();
    }

    /** Removes the entry with this browser reference; returns its pseudonym, or null when there is none. */
    synchronized String remove(String ref) {
        for (Iterator<Entry> it = approved().values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.ref.equals(ref)) {
                it.remove();
                save();
                return e.device;
            }
        }
        return null;
    }

    /** "New key": every approval is gone (and so is every refusal: the pseudonyms change with the booth id anyway). */
    synchronized void clear() {
        approved = new LinkedHashMap<>();
        denied.clear();
        save();
    }

    /** The LJ refused this Mac: for {@value #DENY_MINUTES} minutes it gets {@code reject denied} without a new request. */
    synchronized void deny(String device, long now) {
        if (device == null) {
            return;
        }
        denied.remove(device);
        denied.put(device, now);
        while (denied.size() > MAX_DENIED) {
            denied.remove(denied.keySet().iterator().next());
        }
    }

    synchronized boolean isDenied(String device, long now) {
        Long at = device == null ? null : denied.get(device);
        if (at == null) {
            return false;
        }
        if (now - at >= DENY_NANOS) {
            denied.remove(device);
            return false;
        }
        return true;
    }

    /** Forgets refusals older than {@value #DENY_MINUTES} minutes. */
    synchronized void sweep(long now) {
        denied.values().removeIf(at -> now - at >= DENY_NANOS);
    }

    synchronized int size() {
        return approved().size();
    }

    /**
     * The list for the local API: reference, name, first and last seen (ms since 1970), whether it is connected now
     * ({@code online}: the pseudonyms of the connected approved Macs), last seen first. No pseudonym.
     */
    synchronized List<Map<String, Object>> list(java.util.Set<String> online) {
        List<Entry> sorted = new ArrayList<>(approved().values());
        sorted.sort((a, b) -> Long.compare(b.last, a.last));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry e : sorted) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.ref);
            m.put("name", e.name.isEmpty() ? SenderInbox.DEFAULT_NAME : e.name);
            m.put("first", e.first);
            m.put("last", e.last);
            m.put("connected", online.contains(e.device));
            out.add(m);
        }
        return out;
    }

    /** The pseudonyms in the list (for tests). */
    synchronized java.util.Set<String> devices() {
        return new java.util.HashSet<>(approved().keySet());
    }

    /** The list in {@code settings.json} as {@code [{"device","name","first","last"}]}. */
    static final class SettingsStore implements Store {
        @Override
        public List<Entry> load() {
            List<Entry> out = new ArrayList<>();
            String json = AppSettings.getJson(APPROVED_SETTING);
            if (json == null) {
                return out;
            }
            try {
                for (JsonNode n : Hub.JSON.readTree(json)) {
                    String device = n.path("device").asText("");
                    if (DEVICE.matcher(device).matches()) {
                        out.add(new Entry(device.toLowerCase(Locale.ROOT), SenderClub.clean(n.path("name").asText(""),
                                SenderClub.MAX_NAME), n.path("first").asLong(0), n.path("last").asLong(0)));
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                System.err.println("Could not read the approved DJ Macs; starting with an empty list");
            }
            return out;
        }

        @Override
        public void save(List<Entry> entries) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Entry e : entries) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("device", e.device);
                m.put("name", e.name);
                m.put("first", e.first);
                m.put("last", e.last);
                out.add(m);
            }
            AppSettings.set(APPROVED_SETTING, out);
        }
    }

    // ------------------------------------------------------------------ local API

    /**
     * "New key": a new club key and a new booth id, the list of approved Macs is emptied, every sender and request is
     * disconnected, and Bonjour announces the new booth id. Returns the new key.
     */
    static String newKey(SenderInbox inbox, Runnable reannounce) {
        String key = SenderClub.renewKey();
        SenderClub.renewBooth();
        inbox.forgetEverything();
        reannounce.run();
        System.out.println("Club key and booth id for LJL Link replaced; approved DJ Macs removed");
        return key;
    }

    /**
     * The routes for "New key" and the approval (all under {@code /api/sender/}, so {@link ControlGuard} keeps them to
     * the browser on this computer; nothing here goes to {@code /ws}):
     * <ul>
     * <li>{@code GET /api/sender/pairing}: waiting requests and approved Macs ({@link SenderInbox#pairingState}).</li>
     * <li>{@code POST /api/sender/allow} and {@code /api/sender/deny} with {@code {"id": "r1"}}: 404 when the request is
     * gone; allow answers {@code {"result": "welcomed" | "full"}}.</li>
     * <li>{@code POST /api/sender/approved/remove} with {@code {"id": "m1"}}: 204, 404 when there is no such entry.</li>
     * <li>{@code POST /api/sender/key}: {@link #newKey}, answers {@code {"key": …}}.</li>
     * </ul>
     */
    static void routes(io.javalin.Javalin app, SenderInbox inbox, Runnable reannounce) {
        app.get("/api/sender/pairing", ctx -> ctx.header("Cache-Control", "no-store")
                .json(inbox.pairingState(System.nanoTime())));
        app.post("/api/sender/allow", ctx -> {
            SenderInbox.Allowed a = inbox.allow(id(ctx), System.nanoTime());
            if (a == SenderInbox.Allowed.GONE) {
                ctx.status(404);
            } else {
                ctx.header("Cache-Control", "no-store").json(Map.of("result", a == SenderInbox.Allowed.FULL ? "full" : "welcomed"));
            }
        });
        app.post("/api/sender/deny", ctx -> ctx.status(inbox.deny(id(ctx), System.nanoTime()) ? 204 : 404));
        app.post("/api/sender/approved/remove", ctx -> ctx.status(inbox.removeApproved(id(ctx)) ? 204 : 404));
        app.post("/api/sender/key", ctx -> ctx.header("Cache-Control", "no-store")
                .json(Map.of("key", newKey(inbox, reannounce))));
    }

    /** The {@code id} of a JSON body, or null. */
    private static String id(io.javalin.http.Context ctx) {
        try {
            JsonNode id = Hub.JSON.readTree(ctx.body()).path("id");
            return id.isTextual() ? id.asText() : null;
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
}
