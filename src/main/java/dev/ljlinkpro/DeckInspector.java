package dev.ljlinkpro;

import org.deepsymmetry.beatlink.CdjStatus;
import org.deepsymmetry.beatlink.DeviceAnnouncement;
import org.deepsymmetry.beatlink.DeviceFinder;
import org.deepsymmetry.beatlink.DeviceUpdate;
import org.deepsymmetry.beatlink.MediaDetails;
import org.deepsymmetry.beatlink.MixerStatus;
import org.deepsymmetry.beatlink.VirtualCdj;
import org.deepsymmetry.beatlink.data.AlbumArt;
import org.deepsymmetry.beatlink.data.ArtFinder;
import org.deepsymmetry.beatlink.data.BeatGrid;
import org.deepsymmetry.beatlink.data.BeatGridFinder;
import org.deepsymmetry.beatlink.data.MetadataFinder;
import org.deepsymmetry.beatlink.data.SlotReference;
import org.deepsymmetry.beatlink.data.TimeFinder;
import org.deepsymmetry.beatlink.data.TrackMetadata;
import org.deepsymmetry.beatlink.data.TrackPositionUpdate;
import org.deepsymmetry.beatlink.data.WaveformDetail;
import org.deepsymmetry.beatlink.data.WaveformFinder;
import org.deepsymmetry.beatlink.data.WaveformPreview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Collects everything beat-link knows about one device, grouped into readable sections for the detail view.
 * Read-only: nothing here sends data to the players.
 */
final class DeckInspector {
    private DeckInspector() {}

    /** One section: a title and ordered label/value rows. */
    private static final class Section {
        final String title;
        final List<Map<String, String>> rows = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }

        Section row(String label, Object value) {
            rows.add(Map.of("k", label, "v", value == null ? "–" : String.valueOf(value)));
            return this;
        }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", title);
            m.put("rows", rows);
            return m;
        }
    }

    static List<Map<String, Object>> sections(int n) {
        List<Section> out = new ArrayList<>();
        DeviceAnnouncement ann = null;
        try {
            for (DeviceAnnouncement a : DeviceFinder.getInstance().getCurrentDevices()) {
                if (a.getDeviceNumber() == n) {
                    ann = a;
                }
            }
        } catch (RuntimeException e) {
            // finder stopped
        }
        DeviceUpdate update = null;
        try {
            update = VirtualCdj.getInstance().getLatestStatusFor(n);
        } catch (RuntimeException e) {
            // virtual CDJ stopped
        }

        out.add(device(ann, update));
        if (update instanceof CdjStatus s) {
            safe(out, () -> state(s));
            safe(out, () -> tempo(s));
            safe(out, () -> loop(s));
            safe(out, () -> media(s));
            safe(out, () -> track(n, s));
            safe(out, () -> position(n, s));
            safe(out, () -> cues(n));
            safe(out, () -> data(n));
        } else if (update instanceof MixerStatus m) {
            out.add(new Section("Mixer")
                    .row("Tempo master", yn(m.isTempoMaster()))
                    .row("BPM", String.format(Locale.ROOT, "%.2f", m.getBpm() / 100.0))
                    .row("Beat in bar", m.getBeatWithinBar()));
        }
        if (update != null) {
            final DeviceUpdate u = update;
            safe(out, () -> raw(u));
        }
        List<Map<String, Object>> json = new ArrayList<>();
        for (Section s : out) {
            json.add(s.json());
        }
        return json;
    }

    private static void safe(List<Section> out, Supplier<Section> supplier) {
        try {
            Section s = supplier.get();
            if (s != null) {
                out.add(s);
            }
        } catch (RuntimeException e) {
            out.add(new Section("Error").row("Section not readable", e.toString()));
        }
    }

    private static String yn(boolean b) {
        return b ? "yes" : "no";
    }

    private static String time(long ms) {
        if (ms < 0) {
            return "–";
        }
        long t = ms / 100;
        return String.format("%02d:%02d.%d", t / 600, (t / 10) % 60, t % 10);
    }

    private static Section device(DeviceAnnouncement a, DeviceUpdate u) {
        Section s = new Section("Device");
        s.row("Name", a != null ? a.getDeviceName() : u != null ? u.getDeviceName() : null);
        s.row("Player number", a != null ? a.getDeviceNumber() : u != null ? u.getDeviceNumber() : null);
        s.row("IP address", a != null ? a.getAddress().getHostAddress() : u != null ? u.getAddress().getHostAddress() : null);
        if (a != null) {
            StringBuilder mac = new StringBuilder();
            for (byte b : a.getHardwareAddress()) {
                mac.append(mac.length() == 0 ? "" : ":").append(String.format("%02x", b));
            }
            s.row("MAC address", mac);
            s.row("Devices on the network (peers)", a.getPeerCount());
        }
        if (u instanceof CdjStatus c) {
            s.row("Firmware", c.getFirmwareVersion());
            s.row("Pre-Nexus model", yn(c.isPreNexusCdj()));
        }
        if (u != null) {
            s.row("Last packet", ((System.nanoTime() - u.getTimestamp()) / 1_000_000) + " ms ago");
            s.row("Packet size", u.getPacketBytes().length + " bytes");
        }
        return s;
    }

    private static Section state(CdjStatus s) {
        return new Section("Status")
                .row("Play state 1", s.getPlayState1())
                .row("Play state 2", s.getPlayState2())
                .row("Play state 3", s.getPlayState3())
                .row("Playing", yn(s.isPlaying()))
                .row("Paused", yn(s.isPaused()))
                .row("At cue point", yn(s.isCued()))
                .row("Searching (jog/search)", yn(s.isSearching()))
                .row("At end of track", yn(s.isAtEnd()))
                .row("Reverse", yn(s.isPlayingBackwards()))
                .row("Vinyl mode", yn(s.isPlayingVinylMode()))
                .row("CDJ mode", yn(s.isPlayingCdjMode()))
                .row("Busy (loading)", yn(s.isBusy()))
                .row("Track loaded", yn(s.isTrackLoaded()))
                .row("ON AIR (from mixer)", yn(s.isOnAir()));
    }

    private static Section tempo(CdjStatus s) {
        double pitchPercent = (s.getPitch() - 0x100000) / (double) 0x100000 * 100.0;
        Integer yielding = s.getDeviceMasterIsBeingYieldedTo();
        return new Section("Tempo and sync")
                .row("Track BPM", String.format(Locale.ROOT, "%.2f", s.getBpm() / 100.0))
                .row("Pitch", String.format(Locale.ROOT, "%+.2f %%", pitchPercent))
                .row("Effective BPM", String.format(Locale.ROOT, "%.2f", s.getEffectiveTempo()))
                .row("Tempo master", yn(s.isTempoMaster()))
                .row("Master handoff to", yielding == null ? "none" : "Player " + yielding)
                .row("Sync active", yn(s.isSynced()))
                .row("BPM-only sync", yn(s.isBpmOnlySynced()))
                .row("Sync counter", s.getSyncNumber())
                .row("Beat number", s.getBeatNumber())
                .row("Beat in bar", s.isBeatWithinBarMeaningful() ? s.getBeatWithinBar() : "not available")
                .row("Beats to next cue", s.getCueCountdown())
                .row("Packet counter", s.getPacketNumber());
    }

    private static Section loop(CdjStatus s) {
        Section sec = new Section("Loop").row("Loop active", yn(s.isLooping()));
        if (s.getLoopEnd() > 0) {
            sec.row("Loop start", time(s.getLoopStart()))
                    .row("Loop end", time(s.getLoopEnd()))
                    .row("Loop length", s.getActiveLoopBeats() + " beats");
        }
        return sec;
    }

    private static Section media(CdjStatus s) {
        Section sec = new Section("Media and source")
                .row("Track type", s.getTrackType())
                .row("Source: player", s.getTrackSourcePlayer())
                .row("Source: slot", s.getTrackSourceSlot())
                .row("Rekordbox ID", s.getRekordboxId())
                .row("Track number", s.getTrackNumber())
                .row("USB (local)", s.isLocalUsbLoaded() ? "inserted" : s.isLocalUsbUnloading() ? "being removed" : "empty")
                .row("SD (local)", s.isLocalSdLoaded() ? "inserted" : s.isLocalSdUnloading() ? "being removed" : "empty")
                .row("Link media available", yn(s.isLinkMediaAvailable()))
                .row("CD slot", s.isDiscSlotEmpty() ? "empty" : s.isDiscSlotAsleep() ? "asleep" : "CD with " + s.getDiscTrackCount() + " tracks");
        SlotReference slot = SlotReference.getSlotReference(s.getTrackSourcePlayer(), s.getTrackSourceSlot());
        MediaDetails d = MetadataFinder.getInstance().getMediaDetailsFor(slot);
        if (d != null) {
            sec.row("Media: name", d.name)
                    .row("Media: created", d.creationDate)
                    .row("Media: tracks", d.trackCount)
                    .row("Media: playlists", d.playlistCount)
                    .row("Media: size", d.totalSize / 1_000_000 + " MB")
                    .row("Media: free", d.freeSpace / 1_000_000 + " MB")
                    .row("Media: my settings", yn(d.hasMySettings));
        }
        return sec;
    }

    private static Section track(int n, CdjStatus s) {
        TrackMetadata md = MetadataFinder.getInstance().getLatestMetadataFor(n);
        if (md == null) {
            return new Section("Track").row("Metadata", "not available (streaming track or not loaded yet)");
        }
        return new Section("Track")
                .row("Title", md.getTitle())
                .row("Artist", label(md.getArtist()))
                .row("Album", label(md.getAlbum()))
                .row("Genre", label(md.getGenre()))
                .row("Label", label(md.getLabel()))
                .row("Remixer", label(md.getRemixer()))
                .row("Original artist", label(md.getOriginalArtist()))
                .row("Key", label(md.getKey()))
                .row("BPM (analysis)", String.format(Locale.ROOT, "%.2f", md.getTempo() / 100.0))
                .row("Duration", time(md.getDuration() * 1000L))
                .row("Bitrate", md.getBitRate() + " kbps")
                .row("Year", md.getYear() == 0 ? null : md.getYear())
                .row("Added", md.getDateAdded())
                .row("Rating", md.getRating() + " / 5")
                .row("Color", md.getColor() == null ? null : md.getColor().colorName)
                .row("Comment", md.getComment());
    }

    private static String label(org.deepsymmetry.beatlink.data.SearchableItem item) {
        return item == null ? null : item.label;
    }

    private static Section position(int n, CdjStatus s) {
        Section sec = new Section("Position");
        long t = TimeFinder.getInstance().isRunning() ? TimeFinder.getInstance().getTimeFor(n) : -1;
        sec.row("Current position", t < 0 ? "unknown" : time(t));
        TrackPositionUpdate p = TimeFinder.getInstance().isRunning() ? TimeFinder.getInstance().getLatestPositionFor(n) : null;
        if (p != null) {
            sec.row("Position source", p.precise ? "exact position (CDJ-3000)" : "beat grid calculation")
                    .row("Position definitive", yn(p.definitive))
                    .row("Beat number (position)", p.beatNumber)
                    .row("Playback speed", String.format(Locale.ROOT, "%.4f", p.pitch / (double) 0x100000))
                    .row("Reverse", yn(p.reverse));
        }
        return sec;
    }

    private static Section cues(int n) {
        TrackMetadata md = MetadataFinder.getInstance().getLatestMetadataFor(n);
        if (md == null || md.getCueList() == null || md.getCueList().entries.isEmpty()) {
            return new Section("Cues and loops").row("Entries", "none");
        }
        Section sec = new Section("Cues and loops");
        for (var e : md.getCueList().entries) {
            String kind = e.hotCueNumber > 0 ? "Hot cue " + (char) ('A' + e.hotCueNumber - 1) : "Memory point";
            String text = time(e.cueTime) + (e.isLoop ? " – loop until " + time(e.loopTime) : "");
            if (e.comment != null && !e.comment.isBlank()) {
                text += "  \"" + e.comment + "\"";
            }
            sec.row(kind, text);
        }
        return sec;
    }

    private static Section data(int n) {
        Section sec = new Section("Analysis data");
        WaveformPreview pv = WaveformFinder.getInstance().isRunning() ? WaveformFinder.getInstance().getLatestPreviewFor(n) : null;
        WaveformDetail dt = WaveformFinder.getInstance().isRunning() ? WaveformFinder.getInstance().getLatestDetailFor(n) : null;
        BeatGrid grid = BeatGridFinder.getInstance().isRunning() ? BeatGridFinder.getInstance().getLatestBeatGridFor(n) : null;
        AlbumArt art = ArtFinder.getInstance().isRunning() ? ArtFinder.getInstance().getLatestArtFor(n) : null;
        sec.row("Waveform preview", pv == null ? "not available" : pv.style + ", " + pv.segmentCount + " segments");
        sec.row("Detail waveform", dt == null ? "not available" : dt.style + ", " + dt.getFrameCount() + " Frames, "
                + time(dt.getTotalTime()));
        sec.row("Beat grid", grid == null ? "not available" : grid.beatCount + " beats");
        sec.row("Cover", art == null ? "not available" : art.getRawBytes().remaining() / 1024 + " kB");
        return sec;
    }

    private static Section raw(DeviceUpdate u) {
        byte[] b = u.getPacketBytes();
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i % 16 == 0) {
                hex.append(i == 0 ? "" : "\n").append(String.format("%04x  ", i));
            }
            hex.append(String.format("%02x ", b[i]));
        }
        return new Section("Raw data (status packet, hex)").row("Packet", hex);
    }
}
