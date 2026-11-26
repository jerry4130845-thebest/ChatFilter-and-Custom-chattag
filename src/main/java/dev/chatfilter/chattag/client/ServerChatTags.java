package dev.chatfilter.chattag.client;

import com.mojang.authlib.GameProfile;
import dev.chatfilter.chattag.core.ServerChatFormat;
import dev.chatfilter.chattag.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

/** Configured display-only fallback for packets without a trustworthy sender UUID. */
public final class ServerChatTags {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_NODES = 512;
    private static final int MAX_TEXT = 8192;
    private static final String PUBLIC_CHAT = "chat.type.text";
    private static final String SENDER_MARK = String.valueOf((char) 0);
    private static final String BODY_MARK = String.valueOf((char) 1);

    private ServerChatTags() {}

    static String currentAddress(Minecraft minecraft) {
        return ServerChatFormat.addressKey(remoteAddress(minecraft, minecraft.getConnection()));
    }

    private static String remoteAddress(Minecraft minecraft, ClientPacketListener connection) {
        if (connection == null || minecraft.isLocalServer() || !connection.getConnection().isConnected()
                || connection.getConnection().isMemoryConnection()) return null;
        var data = connection.getServerData();
        return data == null || data.isLan() ? null : data.ip;
    }

    public static Component present(Minecraft minecraft, Component displayed, ChatType.Bound bound) {
        var settings = ClientFeatures.settings();
        if (settings.serverFormats().isEmpty()) return displayed;
        var connection = minecraft.getConnection();
        var format = settings.serverFormatFor(remoteAddress(minecraft, connection));
        if (format == null) return displayed;
        return apply(displayed, bound, format, settings, connection.getLocalGameProfile(), connection.getOnlinePlayers(), minecraft);
    }

    static Component apply(Component displayed, ChatType.Bound bound, ServerChatFormat format, Settings settings,
                           GameProfile local, Collection<PlayerInfo> players) {
        return apply(displayed, bound, format, settings, local, players, null);
    }

    private static Component apply(Component displayed, ChatType.Bound bound, ServerChatFormat format, Settings settings,
                                   GameProfile local, Collection<PlayerInfo> players, Minecraft minecraft) {
        if (format == null || local == null || local.getId() == null || local.getName() == null || local.getName().isEmpty()
                || (bound != null && excluded(bound))) return displayed;
        Settings.Tag tag = settings.tagFor(local.getId(), local.getName());
        if (tag == null) return displayed;
        TextColor color = LegacyText.nameColor(tag.nameColor());
        if (color == null && tag.prefix().isEmpty() && tag.suffix().isEmpty()) return displayed;

        var line = new Line();
        if (!line.read(displayed, bound, true)) return displayed;
        boolean confirmed = local.getId().equals(format.matchOwner());
        String expected = confirmed ? format.matchedName() : format.alias().isEmpty() ? local.getName() : format.alias();
        var author = format.matchName(line.text.toString(), expected);
        if (!insideSender(line, author)) return displayed;
        for (Run run : line.runs) {
            if (run.start >= author.messageStart()) break;
            if (run.style.isObfuscated()) return displayed;
        }
        var name = confirmed ? ServerNameMatches.baseName(expected, local.getName(), format.searchName()) : null;
        if (confirmed && name == null) return displayed;
        for (PlayerInfo player : players) {
            GameProfile other = player.getProfile();
            if (local.getId().equals(other.getId())) continue;
            if (expected.equalsIgnoreCase(other.getName()) || (confirmed
                    && expected.substring(name.start(), name.end()).equalsIgnoreCase(other.getName()))) return displayed;
            Component display = confirmed && minecraft != null ? ServerNameMatches.tabDisplay(minecraft, player) : player.getTabListDisplayName();
            if (hasDisplayName(display, expected)) return displayed;
            if (confirmed && display != null) {
                String slot = ServerNameMatches.project(format, LegacyText.candidateTitle(display, Style.EMPTY));
                if (expected.equalsIgnoreCase(slot)) return displayed;
            }
        }
        if (confirmed) return line.decorate(displayed, author.start() + name.start(), author.start() + name.end(), tag, color);
        // Unconfirmed/manual aliases keep C1 behavior, including the conservative color guard.
        if (color != null && (author.end() - author.start() != local.getName().length()
                || LegacyText.uniqueAccountStart(line.text.subSequence(author.start(), author.end()), local.getName()) != 0)) {
            color = null;
        }
        return line.decorate(displayed, author.start(), author.end(), tag, color);
    }

    private static boolean hasDisplayName(Component display, String expected) {
        if (display == null) return false;
        var probe = new Line();
        return probe.plain(display, Style.EMPTY, 0, expected.length()) && expected.equalsIgnoreCase(probe.text.toString());
    }

    record Heading(String name, String title) {}

    static Heading heading(Component raw, ChatType.Bound bound, ServerChatFormat format) {
        Component component = bound == null ? raw : bound.decorate(raw);
        ServerChatFormat.Author author;
        String text;
        if (component.getContents() instanceof PlainTextContents) {
            var prefix = new HeadingInput(component);
            author = format.parseAuthor(prefix);
            text = prefix.text.toString();
        } else {
            var line = new Line();
            if (!line.read(component, bound, false)) return null;
            text = line.text.toString();
            author = format.parseAuthor(text);
            if (!insideSender(line, author) || line.runs.stream().anyMatch(run -> run.style().isObfuscated())) return null;
        }
        return author == null ? null : new Heading(text.substring(author.start(), author.end()), format.authorTitle(text, author));
    }

    private static boolean insideSender(Line line, ServerChatFormat.Author author) {
        return author != null && (line.senderStart < 0 || (author.start() >= line.senderStart && author.end() <= line.senderEnd
                && author.messageStart() == line.bodyStart));
    }

    /** Visits literal runs lazily: the parser stops at the author's fence, not at the end of a literal. */
    private static final class HeadingInput implements IntSupplier {
        private final ArrayDeque<Frame> stack = new ArrayDeque<>();
        private final StringBuilder text = new StringBuilder();
        private int nodes;

        HeadingInput(Component component) { stack.push(new Frame(component, Style.EMPTY)); }

        @Override
        public int getAsInt() {
            while (!stack.isEmpty()) {
                var frame = stack.peek();
                if (frame.literal == null) {
                    if (stack.size() > MAX_DEPTH + 1 || ++nodes > MAX_NODES
                            || !(frame.component.getContents() instanceof PlainTextContents plain)) return -1;
                    frame.literal = plain.text();
                }
                if (frame.cursor < frame.literal.length()) {
                    if (frame.style.isObfuscated()) return -1;
                    int cp = frame.literal.codePointAt(frame.cursor);
                    frame.cursor += Character.charCount(cp);
                    text.appendCodePoint(cp);
                    return cp;
                }
                List<Component> siblings = frame.component.getSiblings();
                if (frame.child >= siblings.size()) stack.pop();
                else stack.push(new Frame(siblings.get(frame.child++), frame.style));
            }
            return -1;
        }

        private static final class Frame {
            final Component component;
            final Style style;
            String literal;
            int cursor, child;

            Frame(Component component, Style parent) {
                this.component = component;
                this.style = component.getStyle().applyTo(parent);
            }
        }
    }

    static boolean excluded(ChatType.Bound bound) {
        var type = bound.chatType();
        return bound.targetName().isPresent() || type.is(ChatType.SAY_COMMAND) || type.is(ChatType.EMOTE_COMMAND)
                || type.is(ChatType.MSG_COMMAND_INCOMING) || type.is(ChatType.MSG_COMMAND_OUTGOING)
                || type.is(ChatType.TEAM_MSG_COMMAND_INCOMING) || type.is(ChatType.TEAM_MSG_COMMAND_OUTGOING)
                || excludedKey(type.value().chat().translationKey());
    }

    private static boolean excludedKey(String key) {
        return key.equals("chat.type.announcement") || key.equals("chat.type.emote")
                || key.startsWith("commands.message.") || key.startsWith("chat.type.team.")
                || key.equals("chat.type.party") || key.startsWith("chat.type.party.");
    }

    private record Run(int start, int end, Style style) {}

    private static final class Line {
        final StringBuilder text = new StringBuilder();
        final ArrayList<Run> runs = new ArrayList<>();
        int nodes;
        int senderStart = -1, senderEnd, bodyStart;

        boolean read(Component component, ChatType.Bound bound, boolean includeBody) {
            if (component.getContents() instanceof PlainTextContents) return plain(component, Style.EMPTY, 0, MAX_TEXT);
            if (!(component.getContents() instanceof TranslatableContents translation) || !component.getSiblings().isEmpty()) return false;
            String key = translation.getKey();
            String fallback = translation.getFallback();
            if (key.length() > ServerChatFormat.MAX_TEMPLATE || excludedKey(key)
                    || (fallback != null && fallback.length() > ServerChatFormat.MAX_TEMPLATE)
                    || Language.getInstance().getComponent(key) != null) return false;
            int sender = 0, body = 1;
            Object[] args = translation.getArgs();
            if (args.length != 2) return false;
            if (bound == null) {
                if (!key.equals(PUBLIC_CHAT) || fallback != null) return false;
            } else {
                var decoration = bound.chatType().value().chat();
                var parameters = decoration.parameters();
                if (!key.equals(decoration.translationKey()) || parameters.size() != 2) return false;
                sender = parameters.indexOf(ChatTypeDecoration.Parameter.SENDER);
                body = parameters.indexOf(ChatTypeDecoration.Parameter.CONTENT);
                if (sender < 0 || body < 0 || args[sender] != bound.name()) return false;
            }

            // Trace only the translation's fixed literals; neither its sender nor its body is visited here.
            // A repeated, reordered or unrendered slot is unrecognizable and is left alone.
            String translated = Language.getInstance().getOrDefault(key, fallback == null ? key : fallback);
            if (translated.length() > ServerChatFormat.MAX_TEMPLATE || translated.contains(SENDER_MARK) || translated.contains(BODY_MARK)) return false;
            Object[] placeholders = new Object[2];
            placeholders[sender] = SENDER_MARK;
            placeholders[body] = BODY_MARK;
            var shape = new StringBuilder();
            new TranslatableContents(key, fallback, placeholders).visit(part -> {
                shape.append(part);
                return Optional.empty();
            });
            int s = shape.indexOf(SENDER_MARK);
            int b = shape.indexOf(BODY_MARK);
            if (s < 0 || b <= s || b != shape.length() - 1 || shape.lastIndexOf(SENDER_MARK) != s) return false;
            Style style = component.getStyle();
            if (!add(shape.substring(0, s), style, MAX_TEXT)) return false;
            senderStart = text.length();
            if (!argument(args[sender], style)) return false;
            senderEnd = text.length();
            if (!add(shape.substring(s + 1, b), style, MAX_TEXT)) return false;
            bodyStart = text.length();
            return !includeBody || argument(args[body], style);
        }

        private boolean argument(Object arg, Style style) {
            if (arg instanceof Component c) return plain(c, style, 1, MAX_TEXT);
            return arg instanceof String s && add(s, style, MAX_TEXT);
        }

        boolean plain(Component component, Style parent, int depth, int maxText) {
            // Bound the tree itself, not just non-empty visits; empty nested siblings can also be hostile.
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES || !(component.getContents() instanceof PlainTextContents literal)) return false;
            Style style = component.getStyle().applyTo(parent);
            if (!add(literal.text(), style, maxText)) return false;
            for (Component child : component.getSiblings()) {
                if (!plain(child, style, depth + 1, maxText)) return false;
            }
            return true;
        }

        private boolean add(String value, Style style, int maxText) {
            if (value.length() > maxText - text.length() || value.indexOf('§') >= 0
                    || value.codePoints().anyMatch(c -> c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)) return false;
            if (!value.isEmpty()) {
                int start = text.length();
                text.append(value);
                runs.add(new Run(start, text.length(), style));
            }
            return true;
        }

        Component decorate(Component original, int start, int end, Settings.Tag tag, TextColor nameColor) {
            var before = LegacyText.render(tag.prefix(), Style.EMPTY);
            var after = LegacyText.render(tag.suffix(), Style.EMPTY);
            if (nameColor == null && before.getSiblings().isEmpty() && after.getSiblings().isEmpty()) return original;
            var output = Component.empty();
            append(output, 0, start, null);
            if (!before.getSiblings().isEmpty()) output.append(before).append(" ");
            append(output, start, end, nameColor);
            if (!after.getSiblings().isEmpty()) output.append(" ").append(after);
            append(output, end, text.length(), null);
            return output;
        }

        private void append(MutableComponent output, int from, int to, TextColor color) {
            for (Run run : runs) {
                if (run.start >= to) break;
                int start = Math.max(from, run.start), end = Math.min(to, run.end);
                if (start < end) output.append(Component.literal(text.substring(start, end))
                        .setStyle(color == null ? run.style : run.style.withColor(color)));
            }
        }
    }
}
