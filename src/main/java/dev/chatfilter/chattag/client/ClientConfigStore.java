package dev.chatfilter.chattag.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.chatfilter.chattag.core.FilterBook;
import dev.chatfilter.chattag.core.ServerChatFormat;
import dev.chatfilter.chattag.core.Settings;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

public final class ClientConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int LEGACY_VERSION = 1;
    private static final int WORD_LIST_VERSION = 2;
    private static final int SERVER_FORMAT_VERSION = 3;
    private static final int VERSION = 4;
    private final Path path;
    private volatile Settings settings = Settings.defaults();
    private byte[] baseline;
    private volatile Exception loadError;

    public ClientConfigStore(Path path) {
        this.path = path;
        try {
            baseline = read();
            if (baseline != null) settings = decode(baseline);
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            loadError = e;
        }
    }

    public Settings settings() { return settings; }
    public Exception loadError() { return loadError; }

    public synchronized void save(Settings next) throws IOException {
        if (loadError != null) throw new IOException("Existing configuration was unreadable", loadError);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "chatfilter_chattag-", ".tmp");
        try {
            var formats = next.serverFormats().stream().map(FormatData::new).toList();
            byte[] bytes = (GSON.toJson(new FileData(VERSION, next.rules(), next.tags(), formats)) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("Configuration exceeds size limit");
            Files.write(temp, bytes);
            if (!Arrays.equals(read(), baseline)) throw new IOException("Configuration changed on disk; restart before saving");
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            baseline = bytes;
            settings = next;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private byte[] read() throws IOException {
        try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) throw new IOException("Configuration exceeds size limit");
            return data;
        } catch (NoSuchFileException ignored) {
            return null;
        }
    }

    private static Settings decode(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        try (var reader = new JsonReader(new StringReader(text))) {
            JsonElement data = readValue(reader, 0, Settings.MAX_RULES);
            JsonElement version = data.isJsonObject() ? data.getAsJsonObject().get("version") : null;
            boolean legacy = new JsonPrimitive(LEGACY_VERSION).equals(version);
            boolean current = new JsonPrimitive(VERSION).equals(version);
            boolean hasServerFormats = current || new JsonPrimitive(SERVER_FORMAT_VERSION).equals(version);
            if (reader.peek() != JsonToken.END_DOCUMENT
                    || (!legacy && !hasServerFormats && !new JsonPrimitive(WORD_LIST_VERSION).equals(version))) {
                throw new IOException("Unsupported configuration version or trailing data");
            }
            JsonObject root = hasServerFormats ? fields(data, "version", "rules", "tags", "serverFormats")
                    : fields(data, "version", "rules", "tags");
            if (!root.get("rules").isJsonArray() || !root.get("tags").isJsonArray()
                    || (hasServerFormats && !root.get("serverFormats").isJsonArray())) {
                throw new IOException("Expected rules, tags and (since v3) serverFormats arrays");
            }
            var rules = new ArrayList<Settings.Rule>();
            for (JsonElement element : root.getAsJsonArray("rules")) {
                JsonObject r = fields(element, "enabled", "match", "pattern", "action", "replacement");
                JsonElement enabled = r.get("enabled");
                if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
                    throw new IOException("enabled must be boolean");
                }
                rules.add(new Settings.Rule(enabled.getAsBoolean(), Settings.Match.valueOf(text(r, "match")),
                        text(r, "pattern"), Settings.Action.valueOf(text(r, "action")), text(r, "replacement")));
            }
            var tags = new ArrayList<Settings.Tag>();
            for (JsonElement element : root.getAsJsonArray("tags")) {
                JsonObject t = current ? fields(element, "account", "prefix", "suffix", "nameColor")
                        : fields(element, "account", "prefix", "suffix");
                tags.add(new Settings.Tag(text(t, "account"), text(t, "prefix"), text(t, "suffix"),
                        current ? text(t, "nameColor") : ""));
            }
            var formats = new ArrayList<ServerChatFormat>();
            if (hasServerFormats) {
                for (JsonElement element : root.getAsJsonArray("serverFormats")) {
                    if (current && element.isJsonObject() && element.getAsJsonObject().size() != 3) {
                        JsonObject f = fields(element, "address", "template", "alias", "searchName", "matchedName", "matchOwner");
                        String owner = text(f, "matchOwner");
                        java.util.UUID id = owner.isEmpty() ? null : java.util.UUID.fromString(owner);
                        if (id != null && !id.toString().equalsIgnoreCase(owner)) throw new IOException("Invalid match owner");
                        formats.add(new ServerChatFormat(text(f, "address"), text(f, "template"), text(f, "alias"),
                                text(f, "searchName"), text(f, "matchedName"), id));
                    } else {
                        // v3 and the intermediate C1 v4 format have only the manual alias.
                        JsonObject f = fields(element, "address", "template", "alias");
                        formats.add(new ServerChatFormat(text(f, "address"), text(f, "template"), text(f, "alias")));
                    }
                }
            }
            // Only v1 backfills word-list defaults; v1/v2 have no enabled server formats.
            // Migration is persisted only by the normal explicit save.
            return new Settings(legacy ? FilterBook.merge(rules, FilterBook.bundledRules()) : rules, tags, formats);
        }
    }

    private static JsonElement readValue(JsonReader reader, int depth, int maxEntries) throws IOException {
        if (depth > 3) throw new IOException("Unexpected configuration nesting");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key) || object.size() >= 6) throw new IOException("Unexpected configuration key: " + key);
                    int limit = depth == 0 ? switch (key) {
                        case "tags" -> Settings.MAX_TAGS;
                        case "serverFormats" -> Settings.MAX_SERVER_FORMATS;
                        default -> maxEntries;
                    } : maxEntries;
                    object.add(key, readValue(reader, depth + 1, limit));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= maxEntries) throw new IOException("Too many entries");
                    array.add(readValue(reader, depth + 1, maxEntries));
                }
                reader.endArray();
                yield array;
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> switch (reader.nextString()) {
                case "1" -> new JsonPrimitive(LEGACY_VERSION);
                case "2" -> new JsonPrimitive(WORD_LIST_VERSION);
                case "3" -> new JsonPrimitive(SERVER_FORMAT_VERSION);
                case "4" -> new JsonPrimitive(VERSION);
                default -> throw new IOException("Unexpected configuration number");
            };
            default -> throw new IOException("Unexpected configuration value");
        };
    }

    private static JsonObject fields(JsonElement element, String... keys) throws IOException {
        if (!element.isJsonObject() || !element.getAsJsonObject().keySet().equals(Set.of(keys))) {
            throw new IOException("Invalid configuration fields");
        }
        return element.getAsJsonObject();
    }

    private static String text(JsonObject object, String key) throws IOException {
        if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) {
            throw new IOException("Expected string: " + key);
        }
        return object.get(key).getAsString();
    }

    private record FileData(int version, java.util.List<Settings.Rule> rules, java.util.List<Settings.Tag> tags,
                            java.util.List<FormatData> serverFormats) {}
    private record FormatData(String address, String template, String alias, String searchName,
                              String matchedName, String matchOwner) {
        FormatData(ServerChatFormat f) {
            this(f.address(), f.template(), f.alias(), f.searchName(), f.matchedName(),
                    f.matchOwner() == null ? "" : f.matchOwner().toString());
        }
    }
}
