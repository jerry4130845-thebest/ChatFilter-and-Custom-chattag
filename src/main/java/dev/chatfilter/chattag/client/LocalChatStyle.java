package dev.chatfilter.chattag.client;

import com.mojang.authlib.GameProfile;
import dev.chatfilter.chattag.core.OutboundText;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** One tracker per play listener; nothing here goes onto the wire. */
public final class LocalChatStyle {
    private static final int LIMIT = 32;
    private static final long TTL = TimeUnit.SECONDS.toNanos(5);

    public interface Access {
        LocalChatStyle chatfilter$localChatStyle();
    }

    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final LongSupplier clock;
    private Long lastEviction;
    private long nextToken;
    // Only visible during the synchronous main-thread dispatch of the validated packet.
    private Arrival arrival;

    public LocalChatStyle() {
        this(System::nanoTime);
    }

    LocalChatStyle(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized void sent(String raw, ServerboundChatPacket packet) {
        long now = clock.getAsLong();
        expire(now);
        String text = packet.message();
        MessageSignature signature = packet.signature();
        String styleSource = raw != null && raw.indexOf('§') >= 0 && !text.isEmpty()
                && OutboundText.chat(raw).equals(text) ? raw : null;
        // Retain even plain/rewritten sends and already-used entries as ambiguity evidence.
        for (Pending entry : pending) {
            if (entry.text.equals(text)) {
                if (entry.signature == null) entry.raw = null;
                if (signature == null) styleSource = null;
            }
        }
        if (pending.size() == LIMIT) {
            pending.removeFirst();
            lastEviction = now;
        }
        pending.addLast(new Pending(text, signature, now, styleSource));
    }

    public void dispatch(PlayerChatMessage message, MessageSignature wireSignature, ChatType.Bound bound,
                         GameProfile profile, UUID localId, Runnable vanilla) {
        Arrival previous = arrival;
        boolean self = localId != null && localId.equals(message.sender()) && profile != null
                && localId.equals(profile.getId());
        boolean ordinary = self && ChatPresenter.isOrdinaryChat(bound)
                && ChatType.DEFAULT_CHAT_DECORATION.equals(bound.chatType().value().chat());
        arrival = ordinary ? new Arrival(message, claim(message, wireSignature)) : null;
        try {
            vanilla.run();
        } finally {
            arrival = previous;
        }
    }

    public long arrivalToken(PlayerChatMessage message) {
        Arrival current = arrival;
        return current != null && current.message == message ? current.token : 0;
    }

    private synchronized long claim(PlayerChatMessage message, MessageSignature wireSignature) {
        long now = clock.getAsLong();
        expire(now);
        if (wireSignature == null && lastEviction != null) return 0;
        Pending match = null;
        for (Pending entry : pending) {
            if (wireSignature == null ? entry.text.equals(message.signedContent()) : wireSignature.equals(entry.signature)) {
                if (match != null) return 0;
                match = entry;
            }
        }
        if (match == null || match.raw == null || match.token != 0) return 0;
        if (!Objects.equals(wireSignature, match.signature)) return 0;
        if (!Objects.equals(wireSignature, message.signature()) || !match.text.equals(message.signedContent())
                || !message.filterMask().isEmpty() || message.unsignedContent() != null) {
            match.raw = null;
            return 0;
        }
        return match.token = ++nextToken;
    }

    public synchronized Component take(long token, String text) {
        if (token == 0) return null;
        expire(clock.getAsLong());
        for (Pending entry : pending) {
            if (entry.token == token && entry.text.equals(text) && entry.raw != null) {
                String raw = entry.raw;
                entry.raw = null;
                if (entry.signature == null && lastEviction != null) return null;
                return LegacyText.renderChat(raw, text);
            }
        }
        return null;
    }

    private void expire(long now) {
        while (!pending.isEmpty() && now - pending.getFirst().sentAt >= TTL) pending.removeFirst();
        // If an entry was evicted, uniqueness cannot be proved until it would have expired.
        if (lastEviction != null && now - lastEviction >= TTL) lastEviction = null;
    }

    private record Arrival(PlayerChatMessage message, long token) {}

    private static final class Pending {
        final String text;
        final MessageSignature signature;
        final long sentAt;
        String raw;
        long token;

        Pending(String text, MessageSignature signature, long sentAt, String raw) {
            this.text = text;
            this.signature = signature;
            this.sentAt = sentAt;
            this.raw = raw;
        }
    }
}
