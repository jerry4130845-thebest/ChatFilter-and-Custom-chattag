package dev.chatfilter.chattag.mixin.client;

import dev.chatfilter.chattag.client.TextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BookEditScreen.class)
abstract class BookEditScreenMixin {
    @Shadow @Final private TextFieldHelper titleEdit;
    @Shadow private boolean isModified;
    @Shadow private void updateButtonVisibility() { throw new AssertionError(); }

    @Redirect(method = "charTyped(CI)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;isAllowedChatCharacter(C)Z"))
    private boolean chatfilter$pageChar(char c) {
        return TextInput.allowed(c, true);
    }

    @Inject(method = {"bookKeyPressed(III)Z", "titleKeyPressed(III)Z"}, at = @At("HEAD"), cancellable = true)
    private void chatfilter$insertShortcut(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (TextInput.isInsertShortcut(keyCode, modifiers)) {
            cir.setReturnValue(((BookEditScreen) (Object) this).charTyped(TextInput.SECTION_SIGN, modifiers));
        }
    }

    @Inject(method = "titleKeyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void chatfilter$pasteTitle(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!Screen.isPaste(keyCode)) return;
        String clipboard = TextFieldHelper.getClipboardContents(Minecraft.getInstance());
        String text = TextInput.filter(clipboard, false, true);
        if (!text.isEmpty()) {
            titleEdit.insertText(text); // Uses the vanilla book-title validator and selection handling.
            updateButtonVisibility();
            isModified = true;
        }
        cir.setReturnValue(true);
    }
}
