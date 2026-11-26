package dev.chatfilter.chattag.client;

import com.mojang.authlib.GameProfile;
import dev.chatfilter.chattag.core.ServerChatFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Client-thread observations only. Neither opening the UI nor receiving a packet changes Settings. */
public final class ServerNameMatches {
    private static final int MAX_OBSERVATIONS = 24;
    static final int MAX_CANDIDATES = 4;
    private static final Observations OBSERVATIONS = new Observations();

    private ServerNameMatches() {}

    record Scope(String address, UUID connection, UUID owner) {}
    record Query(String template, String searchName) {
        Query(ServerChatFormat format) { this(format.template(), format.searchName()); }
    }
    record Candidate(String name, String title, boolean selectable, int rank) {}
    record Name(int start, int end) {}

    static Scope scope(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        String address = ServerChatTags.currentAddress(minecraft);
        if (address == null || connection == null || connection.getLocalGameProfile().getId() == null) return null;
        return new Scope(address, connection.getId(), connection.getLocalGameProfile().getId());
    }

    private static ServerChatFormat active(Scope scope) {
        if (scope == null) return null;
        var format = ClientFeatures.settings().serverFormatFor(scope.address());
        return format == null || format.searchName().isEmpty() ? null : format;
    }

    /** Called from the vanilla player path after the packet validator, before HUD/filter transformations. */
    public static void observePlayer(Minecraft minecraft, GameProfile profile, ChatType.Bound bound) {
        Scope scope = scope(minecraft);
        var format = active(scope);
        OBSERVATIONS.use(scope, format);
        if (format == null || !scope.owner().equals(profile.getId()) || ServerChatTags.excluded(bound)) return;
        String title = LegacyText.candidateTitle(bound.name(), bound.chatType().value().chat().style());
        OBSERVATIONS.add(candidate(format, profile.getName(), project(format, title), title, true));
    }

    /** Called on original non-overlay system/disguised content, never on a rewritten HUD line. */
    public static void observeTemplate(Minecraft minecraft, Component content, ChatType.Bound bound) {
        Scope scope = scope(minecraft);
        var format = active(scope);
        OBSERVATIONS.use(scope, format);
        if (format == null || (bound != null && ServerChatTags.excluded(bound))) return;
        var heading = ServerChatTags.heading(content, bound, format);
        if (heading == null) return;
        String account = minecraft.getConnection().getLocalGameProfile().getName();
        OBSERVATIONS.add(candidate(format, account, heading.name(), heading.title(), false));
    }

    static List<Candidate> candidates(Minecraft minecraft, ServerChatFormat draft) {
        Scope scope = scope(minecraft);
        OBSERVATIONS.use(scope, active(scope));
        if (scope == null || !scope.address().equals(draft.address()) || draft.searchName().isEmpty()) return List.of();
        var connection = minecraft.getConnection();
        var result = new ArrayList<>(OBSERVATIONS.forQuery(draft));
        // This is vanilla's current packet-backed player info, not the rendered TAB overlay or a HUD scan.
        PlayerInfo self = connection.getPlayerInfo(scope.owner());
        if (self != null) {
            String title = LegacyText.candidateTitle(tabDisplay(minecraft, self), Style.EMPTY);
            Candidate fromTab = candidate(draft, self.getProfile().getName(), project(draft, title), title, true);
            if (fromTab != null) result.add(fromTab);
        }
        var values = new LinkedHashSet<String>();
        return result.stream().sorted(Comparator.comparingInt(Candidate::rank).reversed())
                .filter(c -> values.add(c.name() + '\u0000' + c.title())).limit(MAX_CANDIDATES).toList();
    }

    static Component tabDisplay(Minecraft minecraft, PlayerInfo player) {
        Component display = player.getTabListDisplayName();
        if (display != null || minecraft.level == null) return display;
        var team = player.getTeam();
        return team == null ? null : PlayerTeam.formatNameForTeam(team, Component.literal(player.getProfile().getName()));
    }

    static String project(ServerChatFormat format, String title) {
        if (title == null) return null;
        String projected = format.nameInTitle(title);
        if (projected != null) return projected;
        // With a separate rank, only an unadorned sender slot may stand in for the heading.
        return format.acceptsName(title) && (!format.hasRank() || ServerChatFormat.validSearchName(title)) ? title : null;
    }

    static Name baseName(CharSequence slot, String account, String target) {
        int start = LegacyText.visibleNameStart(slot, account);
        if (start >= 0) return new Name(start, start + account.length());
        start = LegacyText.visibleNameStart(slot, target);
        return start < 0 ? null : new Name(start, start + target.length());
    }

    static Candidate candidate(ServerChatFormat format, String account, String slot, String title, boolean identified) {
        if (slot == null || title == null || !format.acceptsName(slot)) return null;
        boolean target = LegacyText.visibleNameStart(slot, format.searchName()) >= 0;
        if (!identified && !target) return null;
        Name name = baseName(slot, account, format.searchName());
        boolean selectable = name != null;
        int rank = (selectable ? 16 : 0) + (target ? 8 : 0) + (identified ? 4 : 0)
                + (name != null && title.length() > name.end() - name.start() ? 6 : 0);
        return new Candidate(slot, title, selectable, rank);
    }

    static final class Observations {
        private Scope scope;
        private Query query;
        private final LinkedHashSet<Candidate> entries = new LinkedHashSet<>();

        void use(Scope next, ServerChatFormat format) {
            Query nextQuery = format == null || format.searchName().isEmpty() ? null : new Query(format);
            if (!java.util.Objects.equals(scope, next) || !java.util.Objects.equals(query, nextQuery)) entries.clear();
            scope = next;
            query = nextQuery;
        }

        void add(Candidate candidate) {
            if (scope == null || query == null || candidate == null || !entries.add(candidate)) return;
            if (entries.size() > MAX_OBSERVATIONS) entries.removeFirst();
        }

        List<Candidate> forQuery(ServerChatFormat draft) {
            return scope != null && scope.address().equals(draft.address()) && new Query(draft).equals(query)
                    ? List.copyOf(entries) : List.of();
        }
    }
}
