package dev.chatfilter.chattag.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.reporting.ChatReportScreen;
import net.minecraft.client.gui.screens.reporting.NameReportScreen;
import net.minecraft.client.gui.screens.reporting.SkinReportScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

public final class TextInput {
    public static final char SECTION_SIGN = '\u00a7';

    private TextInput() {}

    public static boolean editBox(EditBox box) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof SettingsScreen settings) return settings.isLegacyBox(box);
        if (!(box.getMessage().getContents() instanceof TranslatableContents key)) return false;
        // Many structured fields have the default EditBox predicate; do not infer free text from it.
        return switch (key.getKey()) {
            case "chat.editBox" -> screen instanceof ChatScreen;
            case "container.repair" -> screen instanceof AnvilScreen;
            case "addServer.enterName" -> screen instanceof EditServerScreen;
            case "selectWorld.enterName" -> screen instanceof CreateWorldScreen || screen instanceof EditWorldScreen;
            case "selectWorld.search" -> screen instanceof SelectWorldScreen;
            case "gui.socialInteractions.search_hint" -> screen instanceof SocialInteractionsScreen;
            case "itemGroup.search" -> screen instanceof AbstractContainerScreen<?>;
            default -> false;
        };
    }

    public static boolean isInsertShortcut(int keyCode, int modifiers) {
        int required = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT;
        int keyboardMods = required | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
        return keyCode == GLFW.GLFW_KEY_F8 && (modifiers & keyboardMods) == required;
    }

    // Signs delegate to TextFieldHelper.keyPressed(int), which receives no modifier mask.
    public static boolean isSignInsertShortcut(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_F8 || !(Minecraft.getInstance().screen instanceof AbstractSignEditScreen)) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return Screen.hasShiftDown() && !Screen.hasAltDown()
                && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL))
                && !InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SUPER) && !InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SUPER);
    }

    public static FormattedCharSequence literal(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    public static boolean multiline() {
        Screen screen = Minecraft.getInstance().screen;
        // These vanilla MultiLineEditBox instances are the optional report comment fields.
        return screen instanceof ChatReportScreen || screen instanceof NameReportScreen || screen instanceof SkinReportScreen;
    }

    public static boolean helper() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen;
    }

    public static boolean allowed(char c, boolean freeText) {
        return (freeText && c == SECTION_SIGN) || StringUtil.isAllowedChatCharacter(c);
    }

    public static String filter(String text, boolean allowLineBreaks, boolean freeText) {
        if (!freeText || text.indexOf(SECTION_SIGN) < 0) return StringUtil.filterText(text, allowLineBreaks);
        // Keep vanilla's filter authoritative for every span except the one extra character.
        var result = new StringBuilder(text.length());
        int start = 0;
        for (int i = text.indexOf(SECTION_SIGN); i >= 0; i = text.indexOf(SECTION_SIGN, start)) {
            result.append(StringUtil.filterText(text.substring(start, i), allowLineBreaks)).append(SECTION_SIGN);
            start = i + 1;
        }
        return result.append(StringUtil.filterText(text.substring(start), allowLineBreaks)).toString();
    }
}
