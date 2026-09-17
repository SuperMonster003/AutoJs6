package jackpal.androidterm.emulatorview;

import java.util.ArrayList;
import java.util.List;

/** Package bridge for the bundled terminal's transcript, without reflection or a library fork. */
public final class TerminalSelectionSnapshot {
    public final String text;
    public final int topLine;
    private final List<Integer> softBreaks;

    private TerminalSelectionSnapshot(String text, int topLine, List<Integer> softBreaks) {
        this.text = text;
        this.topLine = topLine;
        this.softBreaks = softBreaks;
    }

    public static TerminalSelectionSnapshot capture(TermSession session, int columns, int topLine) {
        TranscriptScreen screen = session.getEmulator().getScreen();
        int history = screen.getActiveTranscriptRows();
        int end = screen.getActiveRows() - history;
        StringBuilder text = new StringBuilder();
        List<Integer> softBreaks = new ArrayList<>();
        for (int row = -history; row < end; row++) {
            text.append(screen.getSelectedText(0, row, columns - 1, row));
            if (row < end - 1) {
                if (screen.getScriptLineWrap(row)) softBreaks.add(text.length());
                text.append('\n');
            }
        }
        return new TerminalSelectionSnapshot(text.toString(), Math.max(0, topLine), softBreaks);
    }

    /** Remove only display wrapping; keep real line breaks and UTF-16 selection boundaries. */
    public String selectedText(int start, int end) {
        int from = Math.min(text.length(), Math.max(0, Math.min(start, end)));
        int to = Math.min(text.length(), Math.max(0, Math.max(start, end)));
        StringBuilder result = new StringBuilder(text.substring(from, to));
        for (int i = softBreaks.size() - 1; i >= 0; i--) {
            int index = softBreaks.get(i);
            if (index >= from && index < to) result.deleteCharAt(index - from);
        }
        return result.toString();
    }
}
