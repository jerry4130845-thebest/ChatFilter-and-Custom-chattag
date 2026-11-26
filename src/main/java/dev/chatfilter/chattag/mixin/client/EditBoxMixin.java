package dev.chatfilter.chattag.mixin.client;

import dev.chatfilter.chattag.client.TextInput;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EditBox.class)
abstract class EditBoxMixin {
    @Redirect(method = "charTyped(CI)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;isAllowedChatCharacter(C)Z"))
    private boolean chatfilter$typed(char c) {
        return TextInput.allowed(c, c == TextInput.SECTION_SIGN && TextInput.editBox((EditBox) (Object) this));
    }

    @Redirect(method = "insertText(Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;)Ljava/lang/String;"))
    private String chatfilter$inserted(String text) {
        return TextInput.filter(text, false, text.indexOf(TextInput.SECTION_SIGN) >= 0 && TextInput.editBox((EditBox) (Object) this));
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void chatfilter$insertShortcut(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!TextInput.isInsertShortcut(keyCode, modifiers)) return;
        EditBox box = (EditBox) (Object) this;
        if (TextInput.editBox(box) && box.charTyped(TextInput.SECTION_SIGN, modifiers)) cir.setReturnValue(true);
    }

    @Redirect(method = {"renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", "getScreenX(I)I"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int chatfilter$literalWidth(Font font, String text) {
        // EditBox draws the input literally, but vanilla measures these positions as legacy-formatted text.
        return text.indexOf(TextInput.SECTION_SIGN) >= 0 && TextInput.editBox((EditBox) (Object) this)
                ? font.width(TextInput.literal(text)) : font.width(text);
    }
}
