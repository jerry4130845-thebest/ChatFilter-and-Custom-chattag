package dev.chatfilter.chattag.core;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class FilterBook {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final String RESOURCE = "/assets/chatfilter_chattag/default-wordlist.md";
    private static final int BUNDLED_COUNT = 476;

    private FilterBook() {}

    /** Reads a complete local UTF-8 .md or .json file; invalid input yields no rules. */
    public static List<Settings.Rule> read(Path path) throws IOException {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean markdown = name.endsWith(".md");
        if (!markdown && !name.endsWith(".json")) throw new IOException("Expected a .md or .json word list");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Expected a regular word list file");
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            String text = readUtf8(input);
            return markdown ? readMarkdown(text, false) : readJson(text);
        }
    }

    /** Appends only new, exact (match, pattern) pairs, without changing either input list. */
    public static List<Settings.Rule> merge(List<Settings.Rule> existing, List<Settings.Rule> incoming) {
        if (existing.size() > Settings.MAX_RULES || incoming.size() > Settings.MAX_RULES) {
            throw new IllegalArgumentException("Too many rules");
        }
        var keys = new HashSet<RuleKey>();
        var result = new ArrayList<>(existing);
        for (Settings.Rule rule : existing) keys.add(new RuleKey(rule.match(), rule.pattern()));
        for (Settings.Rule rule : incoming) {
            if (keys.add(new RuleKey(rule.match(), rule.pattern()))) {
                if (result.size() >= Settings.MAX_RULES) throw new IllegalArgumentException("Too many rules");
                result.add(rule);
            }
        }
        return List.copyOf(result);
    }

    private record RuleKey(Settings.Match match, String pattern) {}

    /** The additional defaults, in resource order, without the four original rules. */
    public static List<Settings.Rule> bundledRules() {
        return Bundled.RULES;
    }

    private static final class Bundled {
        private static final List<Settings.Rule> RULES = load();

        private static List<Settings.Rule> load() {
            try (var input = FilterBook.class.getResourceAsStream(RESOURCE)) {
                if (input == null) throw new IOException("Missing built-in word list");
                var rules = readMarkdown(readUtf8(input), true);
                if (rules.size() != BUNDLED_COUNT || merge(List.of(), rules).size() != BUNDLED_COUNT
                        || rules.stream().anyMatch(rule -> !rule.enabled())) {
                    throw new IOException("Built-in word list must contain 476 distinct enabled rules");
                }
                return rules;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read built-in word list", e);
            }
        }
    }

    private static String readUtf8(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Word list exceeds 1 MiB");
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }

    private static List<Settings.Rule> readMarkdown(String text, boolean bundled) throws IOException {
        var rules = new ArrayList<Settings.Rule>();
        var lines = text.lines().iterator();
        int lineNumber = 0;
        int fenceLength = 0;
        char fence = 0;
        boolean inComment = false;
        boolean wholeSection = false;
        while (lines.hasNext()) {
            String line = lines.next().strip();
            lineNumber++;
            if (line.isEmpty()) continue;
            char first = line.charAt(0);
            int run = 0;
            if (first == '`' || first == '~') {
                while (run < line.length() && line.charAt(run) == first) run++;
            }
            if (fenceLength > 0) {
                if (first == fence && run >= fenceLength && line.substring(run).isBlank()) fenceLength = 0;
                continue;
            }
            if (inComment || line.startsWith("<!--")) {
                inComment = !line.contains("-->");
                continue;
            }
            if (run >= 3 && (first == '~' || line.indexOf('`', run) < 0)) {
                fence = first;
                fenceLength = run;
                continue;
            }
            // Section 7 of the packaged source only; headings in user files are inert.
            if (bundled && line.startsWith("## ")) wholeSection = line.startsWith("## 7. ");
            if (!line.equals("-") && !line.startsWith("- ") && !line.startsWith("-\t")) continue;
            if (rules.size() >= Settings.MAX_RULES) throw new IOException("Too many rules at Markdown line " + lineNumber);
            try {
                rules.add(markdownRule(line.substring(1).stripLeading(), wholeSection, bundled));
            } catch (IllegalArgumentException e) {
                throw new IOException("Markdown line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(rules);
    }

    private static Settings.Rule markdownRule(String text, boolean wholeSection, boolean bundled) {
        boolean enabled = true;
        if (text.startsWith("[")) {
            if (text.length() < 4 || text.charAt(2) != ']' || !Character.isWhitespace(text.charAt(3))) {
                throw new IllegalArgumentException("expected [x] or [ ] followed by a pattern");
            }
            enabled = switch (text.charAt(1)) {
                case 'x', 'X' -> true;
                case ' ' -> false;
                default -> throw new IllegalArgumentException("expected [x] or [ ]");
            };
            text = text.substring(4).stripLeading();
        }
        String pattern;
        String suffix;
        if (text.startsWith("`")) {
            int end = text.indexOf('`', 1);
            if (end < 0) throw new IllegalArgumentException("unclosed backtick");
            pattern = text.substring(1, end);
            suffix = text.substring(end + 1).stripLeading();
        } else {
            int end = annotationStart(text);
            pattern = text.substring(0, end).stripTrailing();
            suffix = text.substring(end);
        }
        Settings.Action action = Settings.Action.MASK;
        String replacement = "";
        if (!suffix.isEmpty()) {
            if (!suffix.startsWith("—")) throw new IllegalArgumentException("unexpected text after pattern");
            String annotation = suffix.substring(1).stripLeading();
            if (annotation.equals("隐藏整句")) {
                action = Settings.Action.HIDE;
            } else if (annotation.startsWith("替换为：")) {
                action = Settings.Action.REPLACE;
                replacement = annotation.substring("替换为：".length()).strip();
                if (replacement.isEmpty()) throw new IllegalArgumentException("missing replacement text");
            } else if (!annotation.equals("遮词")) {
                throw new IllegalArgumentException("unknown rule annotation");
            }
        }
        boolean shortEnglish = pattern.length() <= (bundled ? 6 : 1) && pattern.chars().allMatch(
                c -> c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z');
        Settings.Match match = wholeSection || shortEnglish ? Settings.Match.WHOLE : Settings.Match.PART;
        return new Settings.Rule(enabled, match, pattern, action, replacement);
    }

    private static int annotationStart(String text) {
        for (int i = text.indexOf('—'); i >= 0; i = text.indexOf('—', i + 1)) {
            if (i == 0 || Character.isWhitespace(text.charAt(i - 1))) return i;
        }
        return text.length();
    }

    private static List<Settings.Rule> readJson(String text) throws IOException {
        try (var reader = new JsonReader(new StringReader(text))) {
            expect(reader, JsonToken.BEGIN_OBJECT, "expected an object with a rules array");
            reader.beginObject();
            if (!reader.hasNext() || !reader.nextName().equals("rules")) throw new IOException("Expected a rules array");
            expect(reader, JsonToken.BEGIN_ARRAY, "rules must be an array");
            reader.beginArray();
            var rules = new ArrayList<Settings.Rule>();
            while (reader.hasNext()) {
                if (rules.size() >= Settings.MAX_RULES) throw new IOException("Too many JSON rules");
                try {
                    rules.add(jsonRule(reader));
                } catch (IOException | IllegalArgumentException e) {
                    throw new IOException("Invalid JSON rule " + (rules.size() + 1) + ": " + e.getMessage(), e);
                }
            }
            reader.endArray();
            if (reader.hasNext()) throw new IOException("Unexpected or duplicate word list field");
            reader.endObject();
            expect(reader, JsonToken.END_DOCUMENT, "trailing data");
            return List.copyOf(rules);
        }
    }

    private static Settings.Rule jsonRule(JsonReader reader) throws IOException {
        expect(reader, JsonToken.BEGIN_OBJECT, "expected a rule object");
        reader.beginObject();
        var fields = new HashSet<String>();
        boolean enabled = true;
        Settings.Match match = Settings.Match.PART;
        Settings.Action action = Settings.Action.MASK;
        String pattern = null;
        String replacement = "";
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (!fields.add(key)) throw new IOException("duplicate rule field");
            switch (key) {
                case "pattern" -> pattern = jsonString(reader);
                case "match" -> match = Settings.Match.valueOf(jsonString(reader));
                case "action" -> action = Settings.Action.valueOf(jsonString(reader));
                case "enabled" -> {
                    expect(reader, JsonToken.BOOLEAN, "enabled must be boolean");
                    enabled = reader.nextBoolean();
                }
                case "replacement" -> replacement = jsonString(reader);
                default -> throw new IOException("unknown rule field");
            }
        }
        reader.endObject();
        if (match == Settings.Match.L_SPAM) {
            if (pattern != null && !pattern.isEmpty()) throw new IOException("L_SPAM requires an empty pattern");
            pattern = "";
        } else if (pattern == null) {
            throw new IOException("missing pattern");
        }
        return new Settings.Rule(enabled, match, pattern, action, replacement);
    }

    private static String jsonString(JsonReader reader) throws IOException {
        expect(reader, JsonToken.STRING, "expected a string");
        return reader.nextString();
    }

    private static void expect(JsonReader reader, JsonToken token, String message) throws IOException {
        if (reader.peek() != token) throw new IOException(message);
    }
}
