package dev.chatfilter.chattag;

import dev.chatfilter.chattag.client.ClientFeatures;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = ChatFilterClient.MOD_ID, dist = Dist.CLIENT)
public final class ChatFilterClient {
    public static final String MOD_ID = "chatfilter_chattag";

    public ChatFilterClient(ModContainer container) {
        ClientFeatures.register(container);
    }
}
