package dev.inputlag;

import dev.inputlag.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Małe pomocnicze funkcje do rozpoznawania slotów. */
final class SlotUtil {
    private SlotUtil() {}

    /** Główny ekwipunek gracza (36 slotów). Pancerz i offhand to indeksy 36+. */
    static boolean isPlayerMain(Slot s) {
        return s.container instanceof Inventory && s.getContainerSlot() < 36;
    }

    /** Slot wyjściowy (wynik craftingu, piec...): nic się tam nie wkłada. */
    static boolean isOutput(Slot s) {
        return !(s.container instanceof Inventory) && s.hasItem() && !s.mayPlace(s.getItem());
    }

    static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    /** Slot pod kursorem (współrzędne w skali GUI), tak jak robi to vanilla. */
    static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        double x = mouseX - acc.inputlag$getLeftPos();
        double y = mouseY - acc.inputlag$getTopPos();
        for (Slot s : screen.getMenu().slots) {
            if (s.isActive() && x >= s.x - 1 && x < s.x + 17 && y >= s.y - 1 && y < s.y + 17) {
                return s;
            }
        }
        return null;
    }
}
