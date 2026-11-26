package dev.chatfilter.chattag.mixin.client;

import dev.chatfilter.chattag.client.TextInput;
import net.minecraft.client.gui.components.MultiLineEditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiLineEditBox.class)
abstract class MultiLineEditBoxMixin {
    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void chatfilter$insertShortcut(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!TextInput.isInsertShortcut(keyCode, modifiers) || !TextInput.multiline()) return;
        MultiLineEditBox box = (MultiLineEditBox) (Object) this;
        if (box.isActive() && box.charTyped(TextInput.SECTION_SIGN, modifiers)) cir.setReturnValue(true);
    }

    @Redirect(method = "charTyped(CI)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;isAllowedChatCharacter(C)Z"))
    private boolean chatfilter$typed(char c) {
        return TextInput.allowed(c, TextInput.multiline());
    }
}
