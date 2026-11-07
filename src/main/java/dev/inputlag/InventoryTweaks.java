package dev.inputlag;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Logika wszystkich tweaków. Jedna instancja na jeden otwarty ekran kontenera.
 * Wszystkie akcje to zwykłe kliknięcia w sloty (PICKUP / QUICK_MOVE), więc serwer
 * widzi to jak normalną grę i mod działa bez instalacji po stronie serwera.
 */
public final class InventoryTweaks {

    private final Minecraft mc;
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;

    private boolean lmbArmed;        // LPM wciśnięty w tym oknie
    private boolean lmbStartedEmpty; // przy wciśnięciu kursor był pusty (vanilla podniosła stack)
    private boolean rmbArmed;        // PPM obsłużony przez mod (vanilla anulowane)
    private Slot lastSlot;           // ostatnio odwiedzony slot

    private InventoryTweaks(Minecraft mc, AbstractContainerScreen<?> screen) {
        this.mc = mc;
        this.screen = screen;
        this.menu = screen.getMenu();
    }

    public static void register() {
        ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
            // Kreatywny ekwipunek ma własną logikę kliknięć - pomijamy.
            if (screen instanceof AbstractContainerScreen<?> acs && !(screen instanceof CreativeModeInventoryScreen)) {
                InventoryTweaks t = new InventoryTweaks(client, acs);
                ScreenMouseEvents.allowMouseClick(screen).register(t::onClick);
                ScreenMouseEvents.allowMouseRelease(screen).register(t::onRelease);
                ScreenMouseEvents.allowMouseScroll(screen).register(t::onScroll);
                ScreenEvents.afterRender(screen).register(t::onAfterRender);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private InputLagConfig cfg() {
        return InputLagConfig.get();
    }

    private void click(Slot slot, int button, ClickType type) {
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, button, type, mc.player);
    }

    /** Czy PPM może położyć 1 sztukę trzymanego przedmiotu na tym slocie? */
    private boolean canPlaceOne(Slot slot, ItemStack carried) {
        if (!slot.isActive() || !slot.mayPlace(carried)) return false;
        if (!slot.hasItem()) return true;
        ItemStack in = slot.getItem();
        return SlotUtil.same(in, carried) && in.getCount() < slot.getMaxStackSize(carried);
    }

    // ------------------------------------------------------------------ mouse events

    private boolean onClick(Screen s, double mx, double my, int button) {
        Slot hovered = SlotUtil.slotAt(screen, mx, my);
        lastSlot = hovered;
        ItemStack carried = menu.getCarried();

        if (button == 0) {
            lmbArmed = true;
            lmbStartedEmpty = carried.isEmpty();
            return true; // vanilla robi swoje (podniesienie stacka / shift-click)
        }

        // RMB Tweak: zastępujemy vanillowe rozkładanie PPM natychmiastowym kliknięciem
        if (button == 1 && cfg().rmbTweak && hovered != null && !carried.isEmpty() && canPlaceOne(hovered, carried)) {
            click(hovered, 1, ClickType.PICKUP); // PPM z przedmiotem = połóż 1 sztukę
            rmbArmed = true;
            return false; // anuluj vanilla (inaczej wystartuje jego tryb drag)
        }
        return true;
    }

    private boolean onRelease(Screen s, double mx, double my, int button) {
        if (button == 1 && rmbArmed) {
            rmbArmed = false;
            return false; // vanilla dołożyłaby jeszcze jedno kliknięcie przy puszczeniu
        }
        if (button == 0) lmbArmed = false;
        return true;
    }

    /** Co klatkę: wykrywa wjazd kursora na nowy slot podczas trzymania przycisku. */
    private void onAfterRender(Screen s, GuiGraphics g, int mx, int my, float dt) {
        if (mc.screen != screen || mc.player == null) return;

        boolean rmbHeld = rmbArmed && mc.mouseHandler.isRightPressed();
        boolean lmbHeld = lmbArmed && mc.mouseHandler.isLeftPressed();
        if (rmbArmed && !rmbHeld) rmbArmed = false;
        if (lmbArmed && !lmbHeld) lmbArmed = false;
        if (!rmbHeld && !lmbHeld) return;

        Slot hovered = SlotUtil.slotAt(screen, mx, my);
        if (hovered == lastSlot) return;
        lastSlot = hovered; // wyjazd ze slotu i powrót liczy się jako nowa wizyta
        if (hovered == null) return;

        ItemStack carried = menu.getCarried();

        // 1) RMB Tweak: dokładaj po 1 sztuce do każdego odwiedzonego slotu
        if (rmbHeld) {
            if (!carried.isEmpty() && canPlaceOne(hovered, carried)) {
                click(hovered, 1, ClickType.PICKUP);
            }
            return;
        }

        boolean shift = Screen.hasShiftDown();
        ItemStack inSlot = hovered.getItem();
        if (inSlot.isEmpty() || SlotUtil.isOutput(hovered) || !hovered.mayPickup(mc.player)) return;

        if (carried.isEmpty()) {
            // 3) LMB bez przedmiotu + Shift: każdy najechany slot -> shift-click
            if (cfg().lmbTweakWithoutItem && shift) {
                click(hovered, 0, ClickType.QUICK_MOVE);
            }
        } else if (cfg().lmbTweakWithItem && lmbStartedEmpty && SlotUtil.same(inSlot, carried)) {
            // 2) LMB z przedmiotem: zbieraj ten sam typ, z Shiftem przenoś do drugiego ekwipunku
            if (shift) {
                click(hovered, 0, ClickType.QUICK_MOVE);
            } else if (hovered.mayPlace(carried)
                    && carried.getCount() + inSlot.getCount() <= hovered.getMaxStackSize(carried)) {
                click(hovered, 0, ClickType.PICKUP); // 1. wrzuć trzymany stack na slot (łączy się)
                click(hovered, 0, ClickType.PICKUP); // 2. podnieś połączony stack
            }
        }
    }

    // ------------------------------------------------------------------ wheel tweak

    private boolean onScroll(Screen s, double mx, double my, double horizontal, double vertical) {
        if (!cfg().wheelTweak || vertical == 0 || mc.player == null) return true;
        if (!menu.getCarried().isEmpty()) return true;

        Slot hovered = SlotUtil.slotAt(screen, mx, my);
        if (hovered == null || !hovered.hasItem() || SlotUtil.isOutput(hovered)) return true;
        if (hovered.container instanceof Inventory && !SlotUtil.isPlayerMain(hovered)) return true; // pancerz/offhand

        // "drugi ekwipunek": dla slotu gracza = wszystko poza graczem, dla reszty = ekwipunek gracza
        boolean fromPlayer = SlotUtil.isPlayerMain(hovered);
        List<Slot> others = new ArrayList<>();
        for (Slot o : menu.slots) {
            if (o == hovered || !o.isActive()) continue;
            boolean isOther = fromPlayer ? !(o.container instanceof Inventory) : SlotUtil.isPlayerMain(o);
            if (isOther) others.add(o);
        }
        if (others.isEmpty()) return true;

        boolean up = vertical > 0;
        boolean push; // true = z kursora do drugiego ekwipunku
        switch (cfg().wheelScrollDirection) {
            case INVERTED -> push = up;
            case POSITION_AWARE -> {
                double avgY = others.stream().mapToInt(o -> o.y).average().orElse(hovered.y);
                boolean otherAbove = avgY < hovered.y;
                push = otherAbove ? up : !up; // scroll "w stronę" drugiego ekwipunku = wysyłanie
            }
            default -> push = !up; // NORMAL: w dół = wypychanie
        }

        int steps = Math.max(1, (int) Math.round(Math.abs(vertical)));
        boolean did = false;
        for (int i = 0; i < steps; i++) {
            boolean ok = push ? pushOne(hovered, others) : pullOne(hovered, others);
            if (!ok) break;
            did = true;
        }
        return !did; // jeśli coś przeniesiono, blokujemy vanillowy scroll
    }

    /** Przenosi 1 sztukę ze slotu 'from' do pasującego slotu z 'others'. */
    private boolean pushOne(Slot from, List<Slot> others) {
        ItemStack stack = from.getItem();
        if (stack.isEmpty() || !from.mayPickup(mc.player) || !from.mayPlace(stack)) return false;

        Slot target = null;
        for (Slot o : others) { // najpierw dopełniamy istniejące stacki
            ItemStack in = o.getItem();
            if (!in.isEmpty() && SlotUtil.same(in, stack) && o.mayPlace(stack) && in.getCount() < o.getMaxStackSize(stack)) {
                target = o;
                break;
            }
        }
        if (target == null) {
            for (Slot o : others) { // potem pierwszy pusty
                if (!o.hasItem() && o.mayPlace(stack)) { target = o; break; }
            }
        }
        if (target == null) return false;

        click(from, 0, ClickType.PICKUP);   // podnieś cały stack
        click(target, 1, ClickType.PICKUP); // PPM = połóż jedną sztukę
        if (!menu.getCarried().isEmpty()) {
            click(from, 0, ClickType.PICKUP); // resztę odłóż tam, skąd wzięliśmy
        }
        return true;
    }

    /** Pobiera 1 sztukę tego samego przedmiotu z 'others' do slotu 'to'. */
    private boolean pullOne(Slot to, List<Slot> others) {
        ItemStack have = to.getItem();
        if (have.isEmpty() || !to.mayPlace(have) || have.getCount() >= to.getMaxStackSize(have)) return false;

        Slot source = null; // bierzemy z najmniejszego stacka
        for (Slot o : others) {
            ItemStack in = o.getItem();
            if (in.isEmpty() || !SlotUtil.same(in, have) || SlotUtil.isOutput(o)) continue;
            if (!o.mayPickup(mc.player) || !o.mayPlace(in)) continue;
            if (source == null || in.getCount() < source.getItem().getCount()) source = o;
        }
        if (source == null) return false;

        click(source, 0, ClickType.PICKUP); // podnieś stack ze źródła
        click(to, 1, ClickType.PICKUP);     // połóż 1 sztukę na slot pod kursorem
        if (!menu.getCarried().isEmpty()) {
            click(source, 0, ClickType.PICKUP); // resztę odłóż
        }
        return true;
    }
}
