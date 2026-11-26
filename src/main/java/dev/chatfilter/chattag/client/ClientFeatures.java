package dev.chatfilter.chattag.client;

import com.mojang.logging.LogUtils;
import dev.chatfilter.chattag.ChatFilterClient;
import dev.chatfilter.chattag.core.OutboundText;
import dev.chatfilter.chattag.core.Settings;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

public final class ClientFeatures {
    static final Logger LOG = LogUtils.getLogger();
    private static volatile ClientConfigStore config;
    private static final Settings DEFAULTS = Settings.defaults();

    private ClientFeatures() {}

    public static void register(ModContainer container) {
        var store = new ClientConfigStore(FMLPaths.CONFIGDIR.get().resolve(ChatFilterClient.MOD_ID + ".json"));
        config = store;
        if (store.loadError() != null) LOG.warn("Could not read chat filter settings; the file will not be overwritten", store.loadError());
        IConfigScreenFactory factory = (mod, parent) -> new SettingsScreen(parent, store);
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientFeatures::sanitizeChat);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SelfNameTag::onRenderNameTag);
    }

    public static Settings settings() {
        ClientConfigStore store = config;
        return store == null ? DEFAULTS : store.settings();
    }

    private static void sanitizeChat(ClientChatEvent event) {
        // This event runs after ChatScreen has routed the message, so an exposed slash stays chat.
        String clean = OutboundText.chat(event.getMessage());
        if (clean.isEmpty()) event.setCanceled(true);
        else event.setMessage(clean);
    }
}
