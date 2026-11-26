package dev.chatfilter.chattag.client;

import com.mojang.authlib.GameProfile;
import dev.chatfilter.chattag.core.RuleEngine;
import dev.chatfilter.chattag.core.ServerChatFormat;
import dev.chatfilter.chattag.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatPresenter {
    private ChatPresenter() {}

    static boolean isOrdinaryChat(ChatType.Bound bound) {
        if (!bound.chatType().is(ChatType.CHAT) || bound.targetName().isPresent()) return false;
        ChatTypeDecoration decoration = bound.chatType().value().chat();
        return decoration.translationKey().equals("chat.type.text") && decoration.parameters().equals(List.of(
                ChatTypeDecoration.Parameter.SENDER, ChatTypeDecoration.Parameter.CONTENT));
    }

    // A null return suppresses only the HUD addition.
    public static Component present(Settings settings, ChatType.Bound bound, PlayerChatMessage message,
                                    GameProfile profile, UUID localId, Component displayed,
                                    LocalChatStyle localStyle, long echoToken) {
        ServerChatFormat format = null;
        if (localId != null && profile != null && localId.equals(profile.getId()) && !settings.serverFormats().isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) format = settings.serverFormatFor(ServerChatTags.currentAddress(minecraft));
        }
        return present(settings, bound, message, profile, localId, displayed, localStyle, echoToken, format);
    }

    public static Component present(Settings settings, ChatType.Bound bound, PlayerChatMessage message,
                                    GameProfile profile, UUID localId, Component displayed,
                                    LocalChatStyle localStyle, long echoToken, ServerChatFormat format) {
        if (profile == null || profile.getId() == null || !profile.getId().equals(message.sender())
                || bound.targetName().isPresent() || message.isFullyFiltered()) return displayed;
        boolean self = profile.getId().equals(localId);
        boolean ordinary = isOrdinaryChat(bound);
        ChatTypeDecoration decoration = bound.chatType().value().chat();
        // The validated player packet supplies the identity. Use the server's explicit SENDER slot,
        // not a name found in the message text. Command/targeted chat still keeps its original name.
        if (!canTagPlayerChat(bound, decoration) || !(displayed.getContents() instanceof TranslatableContents translation)
                || !translation.getKey().equals(decoration.translationKey())) return displayed;
        var parameters = decoration.parameters();
        int senderSlot = parameters.indexOf(ChatTypeDecoration.Parameter.SENDER);
        int bodySlot = parameters.indexOf(ChatTypeDecoration.Parameter.CONTENT);
        Object[] args = translation.getArgs();
        if (args.length != parameters.size() || !(args[senderSlot] instanceof Component sender)
                || !sender.equals(bound.name())) return displayed;

        Object bodyArg = args[bodySlot];
        Object newBody = bodyArg;
        // Unsupported or server-reformatted bodies are left untouched, independently of the name tag.
        if (ordinary && translation.getFallback() == null && bodyArg instanceof Component body) {
            boolean partial = !message.filterMask().isEmpty();
            if (!self && (partial ? isVanillaMaskedBody(body) : body.equals(message.decoratedContent()))) {
                var view = new Body();
                if (view.flatten(body, Style.EMPTY, false, 0) && (!partial || !view.barriers.isEmpty())) {
                    var result = RuleEngine.apply(settings.rules(), view.text.toString(), view.barriers);
                    if (result.hidden()) return null;
                    if (!result.edits().isEmpty()) newBody = view.apply(result.edits());
                }
            } else if (self && localStyle != null && echoToken != 0 && !partial && message.unsignedContent() == null
                    && displayed.equals(bound.decorate(Component.literal(message.signedContent())))) {
                Component styled = localStyle.take(echoToken, message.signedContent());
                if (styled != null) newBody = styled;
            }
        }

        Component newSender = sender;
        Settings.Tag tag = settings.tagFor(profile.getId(), profile.getName());
        if (tag != null) {
            Component matched = self && format != null && localId.equals(format.matchOwner())
                    ? LegacyText.decorateMatchedName(sender, profile.getName(), format, tag, displayed.getStyle()) : null;
            if (matched != null) newSender = matched;
            else {
                Component colored = LegacyText.colorPlayerName(sender, profile.getName(), tag.nameColor(), displayed.getStyle());
                newSender = LegacyText.decorateName(colored, tag.prefix(), tag.suffix());
            }
        }
        if (newSender == sender && newBody == bodyArg) return displayed;
        Object[] updated = args.clone();
        updated[senderSlot] = newSender;
        updated[bodySlot] = newBody;
        MutableComponent output = MutableComponent.create(new TranslatableContents(translation.getKey(), translation.getFallback(), updated))
                .setStyle(displayed.getStyle());
        displayed.getSiblings().forEach(output::append);
        return output;
    }

    private static boolean canTagPlayerChat(ChatType.Bound bound, ChatTypeDecoration decoration) {
        var type = bound.chatType();
        if (type.is(ChatType.SAY_COMMAND) || type.is(ChatType.EMOTE_COMMAND)
                || type.is(ChatType.MSG_COMMAND_INCOMING) || type.is(ChatType.MSG_COMMAND_OUTGOING)
                || type.is(ChatType.TEAM_MSG_COMMAND_INCOMING) || type.is(ChatType.TEAM_MSG_COMMAND_OUTGOING)) return false;
        String key = decoration.translationKey();
        if (key.equals("chat.type.announcement") || key.equals("chat.type.emote")
                || key.startsWith("commands.message.") || key.startsWith("chat.type.team.")) return false;
        var slots = decoration.parameters();
        return slots.size() == 2 && slots.contains(ChatTypeDecoration.Parameter.SENDER)
                && slots.contains(ChatTypeDecoration.Parameter.CONTENT);
    }

    private static boolean isVanillaMaskedBody(Component body) {
        if (!(body.getContents() instanceof PlainTextContents root) || !root.text().isEmpty()
                || !body.getStyle().isEmpty()) return false;
        for (Component part : body.getSiblings()) {
            if (!(part.getContents() instanceof PlainTextContents literal) || !part.getSiblings().isEmpty()) return false;
            if (FilterMask.FILTERED_STYLE.equals(part.getStyle())) {
                if (literal.text().isEmpty() || !literal.text().chars().allMatch(c -> c == '#')) return false;
            } else if (!part.getStyle().isEmpty()) return false;
        }
        return true;
    }

    private record Span(int start, int end, Style style) {}

    private static final class Body {
        final StringBuilder text = new StringBuilder();
        final List<Span> spans = new ArrayList<>();
        final List<RuleEngine.Range> barriers = new ArrayList<>();

        boolean flatten(Component component, Style parent, boolean protectedText, int depth) {
            // Unknown content or embedded raw legacy codes: leave the whole message alone.
            if (depth > 64 || !(component.getContents() instanceof PlainTextContents literal)
                    || literal.text().indexOf('§') >= 0) return false;
            Style own = component.getStyle();
            Style style = own.applyTo(parent);
            protectedText |= FilterMask.FILTERED_STYLE.equals(own);
            if (!literal.text().isEmpty()) {
                int start = text.length();
                text.append(literal.text());
                spans.add(new Span(start, text.length(), style));
                if (protectedText) barriers.add(new RuleEngine.Range(start, text.length()));
            }
            for (Component sibling : component.getSiblings()) {
                if (!flatten(sibling, style, protectedText, depth + 1)) return false;
            }
            return true;
        }

        Component apply(List<RuleEngine.Edit> edits) {
            MutableComponent output = Component.empty();
            int cursor = 0;
            int spanIndex = 0;
            for (RuleEngine.Edit edit : edits) {
                spanIndex = appendOriginal(output, cursor, edit.start(), spanIndex);
                if (!edit.text().isEmpty()) {
                    Style style = spans.get(spanIndex).style().withClickEvent(null).withHoverEvent(null).withInsertion(null);
                    output.append(Component.literal(edit.text()).setStyle(style));
                }
                cursor = edit.end();
            }
            appendOriginal(output, cursor, text.length(), spanIndex);
            return output;
        }

        private int appendOriginal(MutableComponent output, int from, int to, int i) {
            while (i < spans.size()) {
                Span span = spans.get(i);
                if (span.start() >= to) break;
                int start = Math.max(from, span.start());
                int end = Math.min(to, span.end());
                if (start < end) output.append(Component.literal(text.substring(start, end)).setStyle(span.style()));
                if (span.end() > to) break;
                i++;
            }
            return i;
        }
    }
}
