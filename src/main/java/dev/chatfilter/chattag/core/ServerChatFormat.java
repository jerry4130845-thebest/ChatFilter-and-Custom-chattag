package dev.chatfilter.chattag.core;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

/** One explicitly enabled, address-scoped HUD layout. This is not sender authentication. */
public final class ServerChatFormat {
    public static final int MAX_ADDRESS = 260;
    public static final int MAX_TEMPLATE = 256;
    public static final int MAX_ALIAS = 64;
    public static final String RANK_EXAMPLE = "[{rank}] {name}: {message}";
    private static final int DEFAULT_PORT = 25565;
    private final String address;
    private final String template;
    private final String alias;
    private final String searchName;
    private final String matchedName;
    private final UUID matchOwner;
    private final Layout layout;

    public ServerChatFormat(String address, String template, String alias) {
        this(address, template, alias, "", "", null);
    }

    public ServerChatFormat(String address, String template, String alias, String searchName, String matchedName, UUID matchOwner) {
        this.address = addressKey(address);
        if (this.address == null) throw new IllegalArgumentException("invalid server address");
        if (alias == null || (!alias.isEmpty() && !slotText(alias, MAX_ALIAS))) {
            throw new IllegalArgumentException("invalid display alias");
        }
        this.template = template;
        this.alias = alias;
        this.layout = Layout.parse(template);
        if ((!alias.isEmpty() && !acceptsName(alias)) || searchName == null
                || (!searchName.isEmpty() && !validSearchName(searchName)) || matchedName == null
                || (matchOwner == null ? !matchedName.isEmpty()
                        : searchName.isEmpty() || !acceptsName(matchedName))) {
            throw new IllegalArgumentException("invalid server name match");
        }
        this.searchName = searchName;
        this.matchedName = matchedName;
        this.matchOwner = matchOwner;
    }

    public String address() { return address; }
    public String template() { return template; }
    public String alias() { return alias; }
    public String searchName() { return searchName; }
    public String matchedName() { return matchedName; }
    public UUID matchOwner() { return matchOwner; }
    public boolean hasRank() { return layout.afterRank != null; }
    public boolean acceptsName(String name) { return slotText(name, MAX_ALIAS) && !name.contains(layout.afterName.mark()); }

    public static boolean validSearchName(String name) {
        return name != null && slotText(name, MAX_ALIAS) && name.codePoints().allMatch(c -> Character.isLetter(c)
                || switch (Character.getType(c)) {
                    case Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
                            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
                            Character.CONNECTOR_PUNCTUATION -> true;
                    default -> false;
                });
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ServerChatFormat f && address.equals(f.address)
                && template.equals(f.template) && alias.equals(f.alias) && searchName.equals(f.searchName)
                && matchedName.equals(f.matchedName) && Objects.equals(matchOwner, f.matchOwner);
    }

    @Override
    public int hashCode() { return Objects.hash(address, template, alias, searchName, matchedName, matchOwner); }

    /** A local syntactic key, not a resolved address. In particular, no DNS/SRV or endpoint aliases. */
    public static String addressKey(String input) {
        if (input == null || input.isEmpty() || input.length() > MAX_ADDRESS
                || input.codePoints().anyMatch(c -> space(c) || badText(c))) return null;
        int port = DEFAULT_PORT;
        String host;
        String portText = null;
        boolean ipv6;
        if (input.charAt(0) == '[') {
            int close = input.indexOf(']');
            if (close < 0) return null;
            host = input.substring(1, close);
            ipv6 = true;
            if (close + 1 < input.length()) {
                if (input.charAt(close + 1) != ':') return null;
                portText = input.substring(close + 2);
            }
        } else {
            int colon = input.indexOf(':');
            ipv6 = colon >= 0 && colon != input.lastIndexOf(':');
            if (colon >= 0 && !ipv6) {
                host = input.substring(0, colon);
                portText = input.substring(colon + 1);
            } else host = input;
        }
        if (portText != null) {
            if (portText.isEmpty() || portText.length() > 5) return null;
            port = decimal(portText, 0, portText.length());
            if (port < 1 || port > 65535) return null;
        }
        if (host.isEmpty()) return null;
        try {
            if (ipv6) {
                // URI validates bracketed numeric IPv6 without consulting the network. Reject scope IDs.
                if (host.indexOf(':') < 0 || !host.chars().allMatch(c -> c == ':' || c == '.'
                        || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return null;
                URI.create("http://[" + host + "]");
                host = "[" + host.toLowerCase(Locale.ROOT) + "]";
            } else {
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
                int hostLength = host.endsWith(".") ? host.length() - 1 : host.length();
                if (hostLength < 1 || hostLength > 253) return null;
                if (host.chars().allMatch(c -> c == '.' || (c >= '0' && c <= '9')) && !ipv4(host)) return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return port == DEFAULT_PORT ? host : host + ':' + port;
    }

    private static boolean ipv4(String host) {
        int start = 0;
        for (int part = 0; part < 4; part++) {
            int dot = host.indexOf('.', start);
            if ((part == 3) != (dot < 0)) return false;
            int end = dot < 0 ? host.length() : dot;
            if (end == start || end - start > 3 || (end - start > 1 && host.charAt(start) == '0')
                    || decimal(host, start, end) > 255) return false;
            start = end + 1;
        }
        return true;
    }

    private static int decimal(String text, int start, int end) {
        int n = 0;
        for (int i = start; i < end; i++) {
            int d = text.charAt(i) - '0';
            if (d < 0 || d > 9) return -1;
            n = n * 10 + d;
        }
        return n;
    }

    /** UTF-16 offsets into the original rendered text. This method never searches the body for a name. */
    public record Author(int start, int end, int messageStart) {}

    public Author match(String line, String loginName) {
        return matchName(line, alias.isEmpty() ? loginName : alias);
    }

    public Author matchName(String line, String expected) {
        if (expected == null || !acceptsName(expected)) return null;
        var author = parseAuthor(line);
        return author != null && author.end - author.start == expected.length()
                && line.startsWith(expected, author.start) ? author : null;
    }

    public Author parseAuthor(String line) {
        var points = line.codePoints().iterator();
        return parseAuthor(() -> points.hasNext() ? points.nextInt() : -1);
    }

    /** Pulls only the heading through the first exact name fence, never the body of a valid line. */
    public Author parseAuthor(IntSupplier next) {
        if (!consume(next, layout.open, 0)) return null;
        int start = layout.open.length();
        if (layout.afterRank != null) {
            String rank = layout.afterRank.read(next);
            if (rank == null || (!layout.rankOpen.equals(layout.afterRank.mark) && rank.contains(layout.rankOpen))) return null;
            start += rank.length() + layout.afterRank.literal.length();
        }
        String name = layout.afterName.read(next);
        if (name == null) return null;
        int end = start + name.length();
        return new Author(start, end, end + layout.afterName.literal.length());
    }

    /** Projects a complete display heading through the configured layout, without inventing a rank. */
    public String nameInTitle(String title) {
        String line = title + layout.afterName.literal;
        var author = parseAuthor(line);
        return author != null && author.messageStart == line.length() ? title.substring(author.start, author.end) : null;
    }

    public String authorTitle(String head, Author author) {
        int start = hasRank() ? layout.open.lastIndexOf(layout.rankOpen)
                : layout.open.codePoints().anyMatch(Character::isLetterOrDigit) ? 0 : author.start;
        return head.substring(start, author.end).strip();
    }

    private static boolean consume(IntSupplier next, String literal, int start) {
        for (int i = start; i < literal.length();) {
            int cp = literal.codePointAt(i);
            if (next.getAsInt() != cp) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    private static boolean slotText(String text, int max) {
        return !text.isEmpty() && text.length() <= max && !space(text.codePointAt(0))
                && !space(text.codePointBefore(text.length())) && !text.codePoints().anyMatch(ServerChatFormat::badText);
    }

    private static boolean badText(int c) {
        return c == '§' || c == 0x2028 || c == 0x2029 || Character.isISOControl(c)
                || Character.getType(c) == Character.SURROGATE || Character.getType(c) == Character.FORMAT;
    }

    private static boolean space(int c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }

    private static boolean punctuation(int c) {
        return switch (Character.getType(c)) {
            case Character.START_PUNCTUATION, Character.END_PUNCTUATION, Character.OTHER_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                    Character.DASH_PUNCTUATION, Character.MATH_SYMBOL, Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    private record Fence(String literal, String mark, int leadingSpace) {
        static Fence parse(String literal) {
            int i = 0;
            while (i < literal.length() && space(literal.codePointAt(i))) i += Character.charCount(literal.codePointAt(i));
            if (i == literal.length() || !punctuation(literal.codePointAt(i))) throw invalidTemplate();
            return new Fence(literal, Character.toString(literal.codePointAt(i)), i);
        }

        String read(IntSupplier next) {
            var part = new StringBuilder();
            int closing = mark.codePointAt(0);
            while (part.length() <= MAX_ALIAS + leadingSpace) {
                int cp = next.getAsInt();
                if (cp == closing) {
                    // Commit to the first mark. Never retry in what could be the body.
                    int end = part.length() - leadingSpace;
                    if (end < 1 || !part.substring(end).equals(literal.substring(0, leadingSpace))) return null;
                    String slot = part.substring(0, end);
                    return slotText(slot, MAX_ALIAS) && consume(next, literal, leadingSpace + mark.length()) ? slot : null;
                }
                if (cp < 0 || badText(cp)) return null;
                part.appendCodePoint(cp);
            }
            return null;
        }
    }

    private record Layout(String open, String rankOpen, Fence afterRank, Fence afterName) {
        static Layout parse(String template) {
            String name = "{name}", rank = "{rank}", message = "{message}";
            if (template == null || template.length() > MAX_TEMPLATE || !template.endsWith(message)
                    || template.codePoints().anyMatch(ServerChatFormat::badText)) throw invalidTemplate();
            int body = template.length() - message.length();
            int author = template.indexOf(name);
            if (author < 0 || author >= body) throw invalidTemplate();
            int rankStart = template.indexOf(rank);
            if (rankStart >= author) throw invalidTemplate();
            Fence afterName = Fence.parse(literal(template.substring(author + name.length(), body)));
            if (rankStart < 0) return new Layout(literal(template.substring(0, author)), "", null, afterName);
            String open = literal(template.substring(0, rankStart));
            int i = open.length();
            while (i > 0 && space(open.codePointBefore(i))) i -= Character.charCount(open.codePointBefore(i));
            if (i == 0 || !punctuation(open.codePointBefore(i))) throw invalidTemplate();
            return new Layout(open, Character.toString(open.codePointBefore(i)),
                    Fence.parse(literal(template.substring(rankStart + rank.length(), author))), afterName);
        }

        private static String literal(String text) {
            if (text.indexOf('{') >= 0 || text.indexOf('}') >= 0) throw invalidTemplate();
            return text;
        }
    }

    private static IllegalArgumentException invalidTemplate() { return new IllegalArgumentException("invalid server chat template"); }
}
