package dev.chatfilter.chattag.client;

import dev.chatfilter.chattag.core.ServerChatFormat;
import dev.chatfilter.chattag.core.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.regex.Pattern;

public final class LegacyText {
    // Includes Unicode letters, numbers, and combining marks so substrings of other names are not matches.
    private static final Pattern NAME_WORD = Pattern.compile("[\\p{L}\\p{N}\\p{M}\\p{Pc}]+");
    private static final String TITLE_FENCES = "[](){}<>【】（）［］「」『』《》〈〉";

    private LegacyText() {}

    public static MutableComponent render(String input, Style base) {
        return render(input, base, false);
    }

    private static MutableComponent render(String input, Style base, boolean asciiCodesOnly) {
        MutableComponent result = Component.empty();
        var run = new StringBuilder();
        Style style = base;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch != '§') {
                run.append(ch);
                continue;
            }
            // OutboundText recognizes only ASCII codes. Vanilla's Unicode case-fold would also accept K as k.
            ChatFormatting format = i + 1 < input.length() && (!asciiCodesOnly || input.charAt(i + 1) < 128)
                    ? ChatFormatting.getByCode(input.charAt(i + 1)) : null;
            if (format == null) continue;
            if (!run.isEmpty()) {
                result.append(Component.literal(run.toString()).setStyle(style));
                run.setLength(0);
            }
            i++;
            if (format == ChatFormatting.RESET) style = base;
            else if (format.isColor()) {
                style = style.withColor(format).withBold(false).withItalic(false).withUnderlined(false)
                        .withStrikethrough(false).withObfuscated(false);
            } else style = style.applyFormat(format);
        }
        if (!run.isEmpty()) result.append(Component.literal(run.toString()).setStyle(style));
        return result;
    }

    static Component renderChat(String raw, String payload) {
        MutableComponent rendered = render(raw, Style.EMPTY, true);
        if (!rendered.getString().equals(payload)) return null;
        boolean styled = false;
        char last = 0;
        for (Component part : rendered.getSiblings()) {
            String run = part.getString();
            // A component boundary in the middle of a code point would change the rendered text.
            if (Character.isHighSurrogate(last) && Character.isLowSurrogate(run.charAt(0))) return null;
            last = run.charAt(run.length() - 1);
            styled |= !part.getStyle().isEmpty();
        }
        return styled ? rendered : null;
    }

    static TextColor nameColor(String code) {
        if (!Settings.isValidNameColor(code) || code.isEmpty()) return null;
        ChatFormatting format = ChatFormatting.getByCode(code.charAt(1));
        return format != null && format.isColor() ? TextColor.fromLegacyFormat(format) : null;
    }

    /** Offset of one whole account word in an already identified author slot, or -1. */
    static int uniqueAccountStart(CharSequence text, String account) {
        if (!Settings.isAccountName(account)) return -1;
        int start = -1;
        var words = NAME_WORD.matcher(text);
        while (words.find()) {
            if (words.end() - words.start() != account.length()) continue;
            String word = words.group();
            if (!Settings.isAccountName(word) || !word.equalsIgnoreCase(account)) continue;
            if (start >= 0) return -1;
            start = words.start();
        }
        return start;
    }

    static int visibleNameStart(CharSequence text, String name) {
        if (!ServerChatFormat.validSearchName(name)) return -1;
        int start = -1;
        var words = NAME_WORD.matcher(text);
        while (words.find()) {
            String word = words.group();
            if (!(Settings.isAccountName(name) ? Settings.isAccountName(word) && word.equalsIgnoreCase(name) : word.equals(name))) continue;
            if (start >= 0) return -1;
            start = words.start();
        }
        return start < 0 || insideTitle(text, start) ? -1 : start;
    }

    static String candidateTitle(Component sender, Style inherited) {
        if (sender == null) return null;
        var view = new NameView(4 * ServerChatFormat.MAX_ALIAS, true);
        if (!view.read(sender, inherited, 0)) return null;
        String text = view.text.toString();
        // Strip readable legacy formatting only in candidate metadata, never in HUD offsets.
        text = text.replaceAll("§[0-9a-fA-FLlMmNnOoRr]", "").strip();
        return text.isEmpty() || text.indexOf('§') >= 0 ? null : text;
    }

    static Component decorateMatchedName(Component sender, String account, ServerChatFormat format, Settings.Tag tag, Style inherited) {
        var view = new NameView();
        if (!view.read(sender, Style.EMPTY, 0)) return null;
        String title = view.text.toString();
        String slot = ServerNameMatches.project(format, title);
        if (!format.matchedName().equals(slot) || !title.endsWith(slot)) return null;
        var name = ServerNameMatches.baseName(slot, account, format.searchName());
        if (name == null) return null;
        int start = title.length() - slot.length() + name.start(), end = title.length() - slot.length() + name.end();
        if (visibleNameStart(view.text, title.substring(start, end)) != start) return null;
        for (NameRun run : view.runs) if (run.style().applyTo(inherited).isObfuscated()) return null;
        var output = Component.empty();
        var base = Component.empty();
        var tail = Component.empty();
        TextColor color = nameColor(tag.nameColor());
        for (NameRun run : view.runs) {
            view.append(output, run.start(), Math.min(start, run.end()), run.style());
            view.append(base, Math.max(start, run.start()), Math.min(end, run.end()),
                    color == null ? run.style() : run.style().withColor(color));
            view.append(tail, Math.max(end, run.start()), run.end(), run.style());
        }
        return output.append(decorateName(base, tag.prefix(), tag.suffix())).append(tail);
    }

    static Component colorPlayerName(Component sender, String account, String code, Style inherited) {
        TextColor color = nameColor(code);
        if (color == null || !Settings.isAccountName(account)) return sender;
        var view = new NameView();
        if (!view.read(sender, Style.EMPTY, 0)) return sender;
        int start = uniqueAccountStart(view.text, account);
        if (start < 0 || insideTitle(view.text, start)) return sender;
        int end = start + account.length();
        for (NameRun run : view.runs) {
            if (run.start() < end && run.end() > start && run.style().applyTo(inherited).isObfuscated()) return sender;
        }
        var output = Component.empty();
        for (NameRun run : view.runs) {
            int from = Math.max(run.start(), start), to = Math.min(run.end(), end);
            if (from >= to) {
                view.append(output, run.start(), run.end(), run.style());
            } else {
                view.append(output, run.start(), from, run.style());
                view.append(output, from, to, run.style().withColor(color));
                view.append(output, to, run.end(), run.style());
            }
        }
        return output;
    }

    private static boolean insideTitle(CharSequence text, int start) {
        // A token inside a possible rank/title is not evidence that the account was shown there.
        int[] open = new int[TITLE_FENCES.length() / 2];
        for (int i = 0; i < start; i++) {
            int fence = TITLE_FENCES.indexOf(text.charAt(i));
            if (fence < 0) continue;
            int pair = fence / 2;
            if ((fence & 1) == 0) open[pair]++;
            else if (open[pair] > 0) open[pair]--;
        }
        for (int count : open) if (count != 0) return true;
        return false;
    }

    private record NameRun(int start, int end, Style style) {}

    private static final class NameView {
        final StringBuilder text = new StringBuilder();
        final ArrayList<NameRun> runs = new ArrayList<>();
        final int maxText;
        final boolean candidate;
        int nodes;

        NameView() { this(8192, false); }

        NameView(int maxText, boolean candidate) {
            this.maxText = maxText;
            this.candidate = candidate;
        }

        boolean read(Component component, Style parent, int depth) {
            if (depth > 32 || ++nodes > 512 || !(component.getContents() instanceof PlainTextContents plain)) return false;
            String value = plain.text();
            // The name boundary must be unambiguous in the rendered text, not just in getString().
            if (value.length() > maxText - text.length() || (!candidate && value.indexOf('§') >= 0)
                    || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029
                    || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE)) return false;
            Style style = component.getStyle().applyTo(parent);
            if (!value.isEmpty()) {
                if (candidate && style.isObfuscated()) return false;
                int start = text.length();
                text.append(value);
                runs.add(new NameRun(start, text.length(), style));
            }
            for (Component child : component.getSiblings()) {
                if (!read(child, style, depth + 1)) return false;
            }
            return true;
        }

        void append(MutableComponent output, int from, int to, Style style) {
            if (from < to) output.append(Component.literal(text.substring(from, to)).setStyle(style));
        }
    }

    public static Component decorateName(Component name, String prefix, String suffix) {
        if (prefix.isEmpty() && suffix.isEmpty()) return name;
        MutableComponent result = Component.empty();
        MutableComponent before = render(prefix, Style.EMPTY);
        MutableComponent after = render(suffix, Style.EMPTY);
        if (!before.getSiblings().isEmpty()) result.append(before).append(" ");
        result.append(name);
        if (!after.getSiblings().isEmpty()) result.append(" ").append(after);
        return result;
    }
}
