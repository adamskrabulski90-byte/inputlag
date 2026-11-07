package dev.inputlag.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dostęp do chronionych pól ekranu kontenera (pozycja okna GUI). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int inputlag$getLeftPos();

    @Accessor("topPos")
    int inputlag$getTopPos();
}
