package dev.ljlinkpro;

/** A producer of deck state. Pro DJ Link, demo and (later) replay/StageLinQ implement this. */
interface DeckSource {
    /** Starts asynchronously and returns immediately; progress is reported through the {@link Hub}. */
    void start();

    /** Stops everything and releases network resources. Safe to call more than once. */
    void stop();

    /** Everything known about one deck as sections of label/value rows, for the detail view. */
    default java.util.List<java.util.Map<String, Object>> info(int deck) {
        return java.util.List.of();
    }
}
