package dev.chatfilter.chattag.mixin.client;

import dev.chatfilter.chattag.client.TextInput;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.font.TextFieldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextFieldHelper.class)
abstract class TextFieldHelperMixin {
    @Shadow public abstract boolean charTyped(char character);

    @Inject(method = "keyPressed(I)Z", at = @At("HEAD"), cancellable = true)
    private void chatfilter$insertSignShortcut(int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (TextInput.isSignInsertShortcut(keyCode)) cir.setReturnValue(this.charTyped(TextInput.SECTION_SIGN));
    }

    @Redirect(method = "charTyped(C)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;isAllowedChatCharacter(C)Z"))
    private boolean chatfilter$typed(char c) {
        return TextInput.allowed(c, TextInput.helper());
    }

    @Redirect(method = "getClipboardContents(Lnet/minecraft/client/Minecraft;)Ljava/lang/String;", at = @At(value = "INVOKE", target = "Lnet/minecraft/ChatFormatting;stripFormatting(Ljava/lang/String;)Ljava/lang/String;"))
    private static String chatfilter$clipboard(String text) {
        return TextInput.helper() ? text : ChatFormatting.stripFormatting(text);
    }
}
