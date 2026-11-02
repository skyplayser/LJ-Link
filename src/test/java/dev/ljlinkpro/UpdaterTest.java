package dev.ljlinkpro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {
    @Test
    void versionsAreComparedNumerically() {
        assertTrue(Updater.isNewer("1.2.0", "1.1.1"));
        assertTrue(Updater.isNewer("1.10.0", "1.9.3"), "10 is more than 9, not less as text");
        assertTrue(Updater.isNewer("2.0", "1.9.9"));
        assertFalse(Updater.isNewer("1.1.1", "1.1.1"));
        assertFalse(Updater.isNewer("1.1.0", "1.1.1"));
        assertFalse(Updater.isNewer("1.2.0", "dev"), "development builds are never updated");
    }

    @Test
    void theInstallerFitsTheComputer() {
        String name = Updater.assetName();
        assertTrue(name.startsWith("LJ-Link-PRO-") && (name.endsWith(".dmg") || name.endsWith(".exe")));
    }
}
