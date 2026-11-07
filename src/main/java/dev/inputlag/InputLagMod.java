package dev.inputlag;

import net.fabricmc.api.ClientModInitializer;

public final class InputLagMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        InputLagConfig.load();
        InventoryTweaks.register();
    }
}
