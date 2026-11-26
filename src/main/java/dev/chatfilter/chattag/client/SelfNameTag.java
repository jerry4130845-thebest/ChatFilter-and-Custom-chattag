package dev.chatfilter.chattag.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

final class SelfNameTag {
    private SelfNameTag() {}

    static void onRenderNameTag(RenderNameTagEvent event) {
        if (!event.canRender().isDefault()) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getEntity() != player || minecraft.getCameraEntity() != player
                || minecraft.options.getCameraType().isFirstPerson()) return;

        // Keep LivingEntityRenderer's guards other than the camera-entity exclusion.
        if (!Minecraft.renderNames() || player.isInvisibleTo(player) || player.isVehicle()) return;
        float range = player.isDiscrete() ? 32.0F : 64.0F;
        if (minecraft.getEntityRenderDispatcher().distanceToSqr(player) >= range * range) return;

        PlayerTeam team = player.getTeam();
        if (team != null) {
            Team.Visibility visibility = team.getNameTagVisibility();
            if (visibility == Team.Visibility.NEVER || visibility == Team.Visibility.HIDE_FOR_OWN_TEAM) return;
        }

        preferSyncedCustomName(event, player, team);
        // The normal PlayerRenderer still owns positioning, styling and the below-name objective.
        event.setCanRender(TriState.TRUE);
    }

    private static void preferSyncedCustomName(RenderNameTagEvent event, LocalPlayer player, PlayerTeam team) {
        Component original = event.getOriginalContent();
        if (event.getContent() != original) return;
        Component customName = player.getCustomName();
        if (customName == null) return;

        // NeoForge initially shares the original component reference. Only replace an intact
        // 1.21.1 player name, not an in-place edit or a name already decorated by another mod.
        String profileName = player.getGameProfile().getName();
        Component name = Component.literal(profileName);
        Component vanilla = Component.literal("")
                .append(PlayerTeam.formatNameForTeam(team, name))
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + profileName + " "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
                                new HoverEvent.EntityTooltipInfo(player.getType(), player.getUUID(), name)))
                        .withInsertion(profileName));
        if (original.equals(vanilla)) {
            event.setContent(original.plainCopy().setStyle(original.getStyle())
                    .append(PlayerTeam.formatNameForTeam(team, customName)));
        }
    }
}
