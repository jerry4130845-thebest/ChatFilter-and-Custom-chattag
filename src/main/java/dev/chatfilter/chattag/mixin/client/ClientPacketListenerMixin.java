package dev.chatfilter.chattag.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.authlib.GameProfile;
import dev.chatfilter.chattag.client.LocalChatStyle;
import dev.chatfilter.chattag.core.OutboundText;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin implements LocalChatStyle.Access {
    @Unique private final LocalChatStyle chatfilter$echoes = new LocalChatStyle();

    @Shadow public abstract GameProfile getLocalGameProfile();

    @Override
    public LocalChatStyle chatfilter$localChatStyle() {
        return chatfilter$echoes;
    }

    @Inject(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"))
    private void chatfilter$captureRaw(String message, CallbackInfo ci,
                                       @Share("chatfilter$raw") LocalRef<String> raw) {
        raw.set(message);
    }

    @WrapOperation(method = "sendChat(Ljava/lang/String;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, allow = 1)
    private void chatfilter$observeSend(ClientPacketListener listener, Packet<?> packet, Operation<Void> original,
                                        @Share("chatfilter$raw") LocalRef<String> raw) {
        original.call(listener, packet);
        if (packet instanceof ServerboundChatPacket chat) chatfilter$echoes.sent(raw.get(), chat);
    }

    @WrapOperation(method = "handlePlayerChat(Lnet/minecraft/network/protocol/game/ClientboundPlayerChatPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handlePlayerChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/network/chat/ChatType$Bound;)V"),
            require = 1, allow = 1)
    private void chatfilter$validatedEcho(ChatListener listener, PlayerChatMessage message, GameProfile profile,
                                          ChatType.Bound bound, Operation<Void> original, ClientboundPlayerChatPacket packet) {
        chatfilter$echoes.dispatch(message, packet.signature(), bound, profile, getLocalGameProfile().getId(),
                () -> original.call(listener, message, profile, bound));
    }

    @ModifyVariable(method = {"sendCommand(Ljava/lang/String;)V", "sendUnsignedCommand(Ljava/lang/String;)Z"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 2, allow = 2)
    private String chatfilter$sanitizeCommand(String command) {
        return OutboundText.command(command);
    }

    @Inject(method = "sendCommand(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void chatfilter$skipEmptySigned(String command, CallbackInfo ci) {
        // Safe whether this callback observes the original or the already-cleaned argument.
        if (OutboundText.command(command).isEmpty()) ci.cancel();
    }

    @Inject(method = "sendUnsignedCommand(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void chatfilter$skipEmptyUnsigned(String command, CallbackInfoReturnable<Boolean> cir) {
        if (OutboundText.command(command).isEmpty()) cir.setReturnValue(false);
    }
}
