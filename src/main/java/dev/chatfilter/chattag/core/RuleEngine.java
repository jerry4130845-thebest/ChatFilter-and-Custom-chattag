package dev.chatfilter.chattag.core;

import java.util.ArrayList;
import java.util.List;

public final class RuleEngine {
    private RuleEngine() {}

    /** UTF-16 offsets into the original visible body; protected ranges are ordered and disjoint. */
    public record Range(int start, int end) {}
    public record Edit(int start, int end, String text) {}
    public record Result(boolean hidden, List<Edit> edits) {
        public String preview(String original) {
            var text = new StringBuilder(original);
            for (Edit edit : edits.reversed()) text.replace(edit.start(), edit.end(), edit.text());
            return text.toString();
        }
    }

    public static Result apply(List<Settings.Rule> rules, String body, List<Range> protectedRanges) {
        // Only the first matching enabled rule runs. Every match is on the original text.
        for (Settings.Rule rule : rules) {
            if (!rule.enabled()) continue;
            var hits = new ArrayList<Range>();
            switch (rule.match()) {
                case L_SPAM -> {
                    if (protectedRanges.isEmpty() && isLSpam(body)) hits.add(new Range(0, body.length()));
                }
                case WHOLE -> {
                    if (protectedRanges.isEmpty() && body.equals(rule.pattern())) hits.add(new Range(0, body.length()));
                }
                case PART -> {
                    int start = 0;
                    for (Range barrier : protectedRanges) {
                        collect(body, rule.pattern(), start, barrier.start(), hits);
                        start = barrier.end();
                    }
                    collect(body, rule.pattern(), start, body.length(), hits);
                }
            }
            if (hits.isEmpty()) continue;
            if (rule.action() == Settings.Action.HIDE) return new Result(true, List.of());
            String replacement = rule.action() == Settings.Action.MASK ? "***" : rule.replacement();
            return new Result(false, hits.stream().map(r -> new Edit(r.start(), r.end(), replacement)).toList());
        }
        return new Result(false, List.of());
    }

    private static void collect(String body, String needle, int start, int end, List<Range> hits) {
        for (int limit = end - needle.length(); start <= limit;) {
            // Search only this visible segment; never inspect or cross a protected range.
            int match = body.indexOf(needle, start, end);
            if (match < 0) break;
            start = match + needle.length();
            hits.add(new Range(match, start));
        }
    }

    public static boolean isLSpam(String text) {
        boolean sawL = false;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c == 'L' || c == 'l') sawL = true;
            else if (!(Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85 || isPunctuation(c))) return false;
        }
        return sawL;
    }

    private static boolean isPunctuation(int c) {
        return switch (Character.getType(c)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }
}
