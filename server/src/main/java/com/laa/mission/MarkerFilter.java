package com.laa.mission;

/**
 * Removes an end-of-interview marker from streamed text. Chunks can split the marker anywhere, so the tail
 * that could still be the start of a marker is held back until the next chunk (or {@link #finish()}) settles it.
 * Not thread-safe: one instance per stream.
 */
final class MarkerFilter {

    private final String marker;
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder all = new StringBuilder();
    private boolean seen;

    MarkerFilter(String marker) {
        this.marker = marker;
    }

    /** Text from this chunk that is safe to show now (may be empty). */
    String accept(String chunk) {
        pending.append(chunk);
        int at;
        while ((at = pending.indexOf(marker)) >= 0) {
            seen = true;
            pending.delete(at, at + marker.length());
        }
        // Also hold trailing whitespace (and any before a possible marker), so a reply never streams a trailing
        // space the stored, trimmed message doesn't have.
        int keep = heldBack();
        while (keep < pending.length() && Character.isWhitespace(pending.charAt(pending.length() - keep - 1))) keep++;
        String out = pending.substring(0, pending.length() - keep);
        pending.delete(0, pending.length() - keep);
        all.append(out);
        return out;
    }

    /** Whatever was held back, now known not to be a marker, minus trailing whitespace. */
    String finish() {
        String out = pending.toString().stripTrailing();
        pending.setLength(0);
        all.append(out);
        return out;
    }

    boolean markerSeen() {
        return seen;
    }

    /** Everything shown, trimmed: what gets stored as the message. */
    String text() {
        return all.toString().strip();
    }

    /** Length of the longest suffix of pending that is a proper prefix of the marker. */
    private int heldBack() {
        int max = Math.min(pending.length(), marker.length() - 1);
        for (int n = max; n > 0; n--) {
            if (marker.startsWith(pending.substring(pending.length() - n))) return n;
        }
        return 0;
    }
}
