package dev.chatfilter.chattag.mixin.client;

import dev.chatfilter.chattag.client.TextInput;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultilineTextField.class)
abstract class MultilineTextFieldMixin {
    @Redirect(method = "insertText(Ljava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;Z)Ljava/lang/String;"))
    private String chatfilter$inserted(String text, boolean allowLineBreaks) {
        return TextInput.filter(text, allowLineBreaks, TextInput.multiline());
    }
}
