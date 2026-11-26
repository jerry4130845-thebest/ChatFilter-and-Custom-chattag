package dev.chatfilter.chattag.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import dev.chatfilter.chattag.client.ChatPresenter;
import dev.chatfilter.chattag.client.ClientFeatures;
import dev.chatfilter.chattag.client.LocalChatStyle;
import dev.chatfilter.chattag.client.ServerChatTags;
import dev.chatfilter.chattag.client.ServerNameMatches;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.BooleanSupplier;

@Mixin(ChatListener.class)
abstract class ChatListenerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private UUID chatfilter$displayConnection;
    @Unique private long chatfilter$displayToken;
    @Unique private ClientPacketListener chatfilter$disguisedConnection;

    @Inject(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), require = 1)
    private void chatfilter$observeSystem(Component raw, boolean overlay, CallbackInfo ci) {
        if (!overlay) ServerNameMatches.observeTemplate(minecraft, raw, null);
    }

    @Inject(method = "handleDisguisedChatMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"), require = 1)
    private void chatfilter$observeDisguised(Component raw, ChatType.Bound bound, CallbackInfo ci) {
        ServerNameMatches.observeTemplate(minecraft, raw, bound);
    }

    @Inject(method = "handlePlayerChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"), require = 1)
    private void chatfilter$observePlayer(PlayerChatMessage message, GameProfile profile, ChatType.Bound bound, CallbackInfo ci) {
        if (message.sender().equals(profile.getId())) ServerNameMatches.observePlayer(minecraft, profile, bound);
    }

    @ModifyArg(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;)V"),
            index = 0, require = 1, allow = 1)
    private Component chatfilter$systemHud(Component displayed) {
        return ServerChatTags.present(minecraft, displayed, null);
    }

    @WrapOperation(method = "handleDisguisedChatMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleMessage(Lnet/minecraft/network/chat/MessageSignature;Ljava/util/function/BooleanSupplier;)V"),
            require = 1, allow = 1)
    private void chatfilter$bindDisguisedHud(ChatListener listener, MessageSignature signature, BooleanSupplier handle,
                                           Operation<Void> original) {
        ClientPacketListener arrival = minecraft.getConnection();
        // This supplier may be delayed. Do not apply the next connection's settings to an earlier message.
        original.call(listener, signature, (BooleanSupplier) () -> {
            ClientPacketListener previous = chatfilter$disguisedConnection;
            chatfilter$disguisedConnection = arrival;
            try {
                return handle.getAsBoolean();
            } finally {
                chatfilter$disguisedConnection = previous;
            }
        });
    }

    @WrapOperation(method = "lambda$handleDisguisedChatMessage$3(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/Component;Ljava/time/Instant;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;)V"),
            require = 1, allow = 1)
    private void chatfilter$disguisedHud(ChatComponent hud, Component displayed, Operation<Void> original, ChatType.Bound bound) {
        if (chatfilter$disguisedConnection != null && chatfilter$disguisedConnection == minecraft.getConnection()) {
            displayed = ServerChatTags.present(minecraft, displayed, bound);
        }
        original.call(hud, displayed);
    }

    @WrapOperation(method = "handlePlayerChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleMessage(Lnet/minecraft/network/chat/MessageSignature;Ljava/util/function/BooleanSupplier;)V"),
            require = 1, allow = 1)
    private void chatfilter$bindDisplay(ChatListener listener, MessageSignature signature, BooleanSupplier handle,
                                        Operation<Void> original, PlayerChatMessage message) {
        ClientPacketListener connection = minecraft.getConnection();
        UUID id = connection == null ? null : connection.getId();
        long token = connection instanceof LocalChatStyle.Access access ? access.chatfilter$localChatStyle().arrivalToken(message) : 0;
        // The vanilla supplier may run later. Scope only its HUD presentation to this play listener.
        original.call(listener, signature, (BooleanSupplier) () -> {
            UUID previousId = chatfilter$displayConnection;
            long previousToken = chatfilter$displayToken;
            chatfilter$displayConnection = id;
            chatfilter$displayToken = token;
            try {
                return handle.getAsBoolean();
            } finally {
                chatfilter$displayConnection = previousId;
                chatfilter$displayToken = previousToken;
            }
        });
    }

    @WrapOperation(method = "showMessageToPlayer(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/Component;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"), require = 2, allow = 2)
    private void chatfilter$display(ChatComponent hud, Component displayed, MessageSignature signature, GuiMessageTag tag,
                                    Operation<Void> original, ChatType.Bound bound, PlayerChatMessage message,
                                    Component decorated, GameProfile profile) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || !connection.getId().equals(chatfilter$displayConnection)) {
            original.call(hud, displayed, signature, tag);
            return;
        }
        var localId = connection.getLocalGameProfile().getId();
        LocalChatStyle localStyle = connection instanceof LocalChatStyle.Access access ? access.chatfilter$localChatStyle() : null;
        Component result = ChatPresenter.present(ClientFeatures.settings(), bound, message, profile, localId, displayed,
                localStyle, chatfilter$displayToken);
        if (result != null) original.call(hud, result, signature, tag);
    }
}
