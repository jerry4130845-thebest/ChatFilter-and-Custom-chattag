package dev.chatfilter.chattag.core;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Settings(List<Rule> rules, List<Tag> tags, List<ServerChatFormat> serverFormats) {
    public static final int MAX_RULES = 2048;
    public static final int MAX_TAGS = 128;
    public static final int MAX_SERVER_FORMATS = 64;
    public static final int MAX_ENTRIES = MAX_TAGS; // Compatibility for existing UI callers.
    public static final int MAX_RULE_TEXT = 256;
    public static final int MAX_AFFIX = 96;
    public static final int MAX_NAME_COLOR = 2;
    public static final int MAX_ACCOUNT = 36;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Pattern NAME_COLOR = Pattern.compile("§[0-9a-fA-FrR]");

    public static boolean isAccountName(String text) {
        return text != null && NAME.matcher(text).matches();
    }

    public static boolean isValidNameColor(String text) {
        return text != null && (text.isEmpty() || NAME_COLOR.matcher(text).matches());
    }

    public Settings(List<Rule> rules, List<Tag> tags) {
        this(rules, tags, List.of());
    }

    public Settings {
        rules = List.copyOf(rules);
        tags = List.copyOf(tags);
        serverFormats = List.copyOf(serverFormats);
        if (rules.size() > MAX_RULES) throw new IllegalArgumentException("rule limit");
        if (tags.size() > MAX_TAGS) throw new IllegalArgumentException("tag limit");
        if (serverFormats.size() > MAX_SERVER_FORMATS) throw new IllegalArgumentException("server format limit");
        var accounts = new HashSet<String>();
        for (Tag tag : tags) {
            if (!accounts.add(tag.account())) throw new IllegalArgumentException("duplicate account");
        }
        var addresses = new HashSet<String>();
        for (ServerChatFormat format : serverFormats) {
            if (!addresses.add(format.address())) throw new IllegalArgumentException("duplicate server address");
        }
    }

    public ServerChatFormat serverFormatFor(String address) {
        if (serverFormats.isEmpty()) return null;
        String key = ServerChatFormat.addressKey(address);
        for (ServerChatFormat format : serverFormats) {
            if (format.address().equals(key)) return format;
        }
        return null;
    }

    public static Settings defaults() {
        return Defaults.VALUE;
    }

    private static final class Defaults {
        private static final Settings VALUE = new Settings(FilterBook.merge(List.of(
                new Rule(true, Match.L_SPAM, "", Action.REPLACE, "love you"),
                new Rule(true, Match.PART, "ez", Action.REPLACE, "gg"),
                new Rule(true, Match.PART, "noob", Action.MASK, ""),
                new Rule(true, Match.WHOLE, "垃圾队友", Action.HIDE, "")
        ), FilterBook.bundledRules()), List.of());
    }

    public Tag tagFor(UUID id, String name) {
        String uuid = id == null ? "" : id.toString();
        Tag byName = null;
        for (Tag tag : tags) {
            if (tag.account().equals(uuid)) return tag;
            if (tag.account().equalsIgnoreCase(name)) byName = tag;
        }
        return byName;
    }

    public enum Match { L_SPAM, WHOLE, PART }
    public enum Action { REPLACE, MASK, HIDE }

    public record Rule(boolean enabled, Match match, String pattern, Action action, String replacement) {
        public Rule {
            Objects.requireNonNull(match);
            Objects.requireNonNull(action);
            checkText(pattern, MAX_RULE_TEXT, false);
            checkText(replacement, MAX_RULE_TEXT, false);
            if (match != Match.L_SPAM && pattern.isBlank()) throw new IllegalArgumentException("empty pattern");
        }
    }

    public record Tag(String account, String prefix, String suffix, String nameColor) {
        public Tag(String account, String prefix, String suffix) {
            this(account, prefix, suffix, "");
        }

        public Tag {
            checkText(account, MAX_ACCOUNT, false);
            if (!isAccountName(account) && !UUID_TEXT.matcher(account).matches()) {
                throw new IllegalArgumentException("invalid account");
            }
            account = account.toLowerCase(Locale.ROOT);
            checkText(prefix, MAX_AFFIX, true);
            checkText(suffix, MAX_AFFIX, true);
            if (!isValidNameColor(nameColor)) throw new IllegalArgumentException("invalid name color");
            nameColor = nameColor.toLowerCase(Locale.ROOT);
        }
    }

    private static void checkText(String text, int maxLength, boolean legacy) {
        if (text.length() > maxLength || text.codePoints().anyMatch(c -> Character.isISOControl(c)
                || c == 0x2028 || c == 0x2029 || (c >= 0xd800 && c <= 0xdfff) || (!legacy && c == '§'))) {
            throw new IllegalArgumentException("invalid text");
        }
    }
}
