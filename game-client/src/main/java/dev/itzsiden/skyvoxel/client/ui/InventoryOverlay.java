package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.engine.gameplay.PlayerStats;
import dev.itzsiden.skyvoxel.client.items.ItemVisualRegistry;
import dev.itzsiden.skyvoxel.engine.items.InventoryModel;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinition;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinitionRegistry;
import dev.itzsiden.skyvoxel.engine.items.ItemEquipmentSlot;
import dev.itzsiden.skyvoxel.engine.items.ItemRarity;
import dev.itzsiden.skyvoxel.engine.items.ItemStatModifier;
import dev.itzsiden.skyvoxel.engine.items.ItemStack;
import dev.itzsiden.skyvoxel.engine.stats.StatDefinition;
import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseButton;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clean, renderer-independent inventory/equipment/stats overlay and hit testing. */
public final class InventoryOverlay {
    private static final int BG = 0xF0131922;
    private static final int PANEL = 0xF018202B;
    private static final int SLOT = 0xFF202A36;
    private static final int SLOT_HOVER = 0xFF2B3948;
    private static final int BORDER = 0xFF3B4B5B;
    private static final int ACCENT = 0xFF55C9D5;
    private static final int TEXT = 0xFFF0F4F7;
    private static final int MUTED = 0xFF9EADBA;
    private static final int SUBTLE = 0xFF657586;
    private boolean visible;
    private int viewportWidth = 1280, viewportHeight = 720;
    private double mouseX = -1, mouseY = -1;
    private Layout lastLayout = new Layout(0, 0, 1, 0, 0, 0, 0, 0, List.of(), List.of());
    private final boolean[] previousNumberKeys = new boolean[InventoryModel.HOTBAR_SIZE];
    private ItemVisualRegistry itemVisuals;

    public boolean visible() { return visible; }
    public void setItemVisuals(ItemVisualRegistry visuals) { this.itemVisuals = visuals; }

    public void updateHotbarKeys(RenderWindow window, InventoryModel inventory) {
        for (int i = 0; i < InventoryModel.HOTBAR_SIZE; i++) {
            boolean down = window.isKeyDown(numberKey(i));
            if (down && !previousNumberKeys[i] && !visible) inventory.setSelectedHotbarIndex(i);
            previousNumberKeys[i] = down;
        }
    }

    public boolean closeWithCursorRecovery(InventoryModel inventory, ItemDefinitionRegistry catalog) {
        if (!inventory.recoverCursor(catalog)) return false;
        visible = false;
        return true;
    }

    public void setVisible(boolean value) { visible = value; }

    /** Process a new mouse press; hotbar clicks work in-world, slot interactions work while open. */
    public boolean handleMouse(int width, int height, double x, double y, boolean leftPressed,
    boolean rightPressed, boolean shiftDown, InventoryModel inventory,
    ItemDefinitionRegistry catalog) {
        mouseX = x; mouseY = y;
        lastLayout = layout(Math.max(1, width), Math.max(1, height));
        if (!visible) {
            if (leftPressed) {
                SlotAddress hit = hit(lastLayout.hotbarSlots(), x, y);
                if (hit != null) {
                    inventory.setSelectedHotbarIndex(hit.index() - InventoryModel.HOTBAR_START);
                    return true;
                }
            }
            return false;
        }
        if (!leftPressed && !rightPressed) return false;
        SlotAddress hit = hit(lastLayout.inventorySlots(), x, y);
        if (hit == null) hit = hit(lastLayout.equipmentSlots(), x, y);
        if (hit == null) return false;
        if (shiftDown && hit.kind() == SlotKind.STORAGE && leftPressed) {
            quickMove(hit.index(), inventory, catalog);
            return true;
        }
        if (rightPressed) rightClick(hit, inventory, catalog);
        else leftClick(hit, inventory, catalog);
        return true;
    }

    public UiDrawList build(int width, int height, InventoryModel inventory, ItemDefinitionRegistry catalog, PlayerStats stats) {
        viewportWidth = Math.max(1, width); viewportHeight = Math.max(1, height);
        lastLayout = layout(viewportWidth, viewportHeight);
        UiDrawList.Builder draw = new UiDrawList.Builder();
        if (visible) {
            draw.fillRect(0, 0, width, height, 0x8A05090E);
            draw.roundedRect(lastLayout.x(), lastLayout.y(), lastLayout.width(), lastLayout.height(), BG, 7 * lastLayout.scale());
            draw.strokeRoundedRect(lastLayout.x(), lastLayout.y(), lastLayout.width(), lastLayout.height(), 0xFF354454, 1.1f * lastLayout.scale(), 7 * lastLayout.scale());
            draw.fillRect(lastLayout.x(), lastLayout.y(), lastLayout.width(), 2.0f * lastLayout.scale(), ACCENT);
            
            // Clean professional header and minimal controls hint
            draw.text(lastLayout.x() + 24 * lastLayout.scale(), lastLayout.y() + 22 * lastLayout.scale(), "INVENTORY", 18 * lastLayout.scale(), TEXT, true, false, UiDrawList.TextAlign.LEFT);
            draw.text(lastLayout.x() + lastLayout.width() - 22 * lastLayout.scale(), lastLayout.y() + 25 * lastLayout.scale(),
            "ESC / I TO CLOSE", 9.0f * lastLayout.scale(), MUTED, false, false, UiDrawList.TextAlign.RIGHT);
            
            draw.line(lastLayout.x() + 22 * lastLayout.scale(), lastLayout.y() + 53 * lastLayout.scale(),
            lastLayout.x() + lastLayout.width() - 22 * lastLayout.scale(), lastLayout.y() + 53 * lastLayout.scale(), BORDER, 1 * lastLayout.scale());
            
            draw.text(lastLayout.invX(), lastLayout.invY() - 20 * lastLayout.scale(), "STORAGE", 10 * lastLayout.scale(), MUTED, true, false, UiDrawList.TextAlign.LEFT);
            for (SlotAddress slot : lastLayout.inventorySlots()) drawSlot(draw, slot, inventory, catalog, mouseX, mouseY, lastLayout.scale());
            
            draw.text(lastLayout.invX(), lastLayout.invY() + (2 * 47 + 42 + 2) * lastLayout.scale(), "HOTBAR", 7.5f * lastLayout.scale(), SUBTLE, true, false, UiDrawList.TextAlign.LEFT);
            draw.text(lastLayout.detailX(), lastLayout.detailY() - 18 * lastLayout.scale(), "INSPECTION", 10 * lastLayout.scale(), MUTED, true, false, UiDrawList.TextAlign.LEFT);
            drawDetails(draw, inventory, catalog, lastLayout);
            
            draw.text(lastLayout.equipX(), lastLayout.equipY() - 20 * lastLayout.scale(), "EQUIPMENT", 10 * lastLayout.scale(), MUTED, true, false, UiDrawList.TextAlign.LEFT);
            for (SlotAddress slot : lastLayout.equipmentSlots()) drawSlot(draw, slot, inventory, catalog, mouseX, mouseY, lastLayout.scale());
            
            drawStats(draw, stats, lastLayout);
            
            if (inventory.cursorStack() != null && mouseX >= 0 && mouseY >= 0) {
                drawSlotAtCursor(draw, inventory.cursorStack(), catalog, mouseX, mouseY, lastLayout.scale());
            }
        } else {
            drawHotbar(draw, inventory, catalog, width, height, lastLayout.scale(), mouseX, mouseY);
        }
        return draw.build();
    }

    private void drawDetails(UiDrawList.Builder draw, InventoryModel inventory, ItemDefinitionRegistry catalog, Layout l) {
        draw.roundedRect(l.detailX(), l.detailY(), l.detailW(), l.detailH(), PANEL, 4 * l.scale());
        draw.strokeRoundedRect(l.detailX(), l.detailY(), l.detailW(), l.detailH(), BORDER, 0.8f * l.scale(), 4 * l.scale());
        ItemStack stack = hoveredStack(inventory, l);
        if (stack == null) stack = inventory.cursorStack();
        if (stack == null) {
            draw.text(l.detailX() + 16 * l.scale(), l.detailY() + 24 * l.scale(), "No Item Selected", 11.5f * l.scale(), TEXT, true, false, UiDrawList.TextAlign.LEFT);
            draw.text(l.detailX() + 16 * l.scale(), l.detailY() + 46 * l.scale(), "Hover over an item to inspect its attributes.", 9.0f * l.scale(), MUTED, false, false, UiDrawList.TextAlign.LEFT);
            return;
        }
        ItemDefinition item = catalog.require(stack.itemId());
        float textX = l.detailX() + 12 * l.scale();
        if (itemVisuals != null) {
            var icon = itemVisuals.icon(item.id());
            if (icon.isPresent()) {
                float iconSize = 62 * l.scale();
                draw.roundedRect(l.detailX() + 7 * l.scale(), l.detailY() + 8 * l.scale(), iconSize, iconSize, 0xFF202A36, 4 * l.scale());
                draw.image(l.detailX() + 7 * l.scale(), l.detailY() + 8 * l.scale(), iconSize, iconSize, icon.get());
                textX = l.detailX() + 78 * l.scale();
            }
        }
        draw.text(textX, l.detailY() + 21 * l.scale(), item.displayName(), 12.5f * l.scale(), item.rarity().colorArgb(), true, false, UiDrawList.TextAlign.LEFT);
        draw.text(l.detailX() + l.detailW() - 12 * l.scale(), l.detailY() + 20 * l.scale(), item.rarity().displayName().toUpperCase(Locale.ROOT), 8.4f * l.scale(), item.rarity().colorArgb(), true, false, UiDrawList.TextAlign.RIGHT);
        draw.text(textX, l.detailY() + 39 * l.scale(), item.categoryId().toUpperCase(Locale.ROOT) + "  ·  " + item.id(), 8.2f * l.scale(), MUTED, false, false, UiDrawList.TextAlign.LEFT);
        if (!item.description().isBlank()) draw.text(textX, l.detailY() + 57 * l.scale(), trim(item.description(), 38), 9.0f * l.scale(), TEXT, false, false, UiDrawList.TextAlign.LEFT);
        List<ItemStatModifier> displayedModifiers = new ArrayList<>(item.modifiers().stream().limit(2).toList());
        displayedModifiers.addAll(stack.instanceModifiers().stream().limit(Math.max(0, 4 - displayedModifiers.size())).toList());
        int row = 0;
        for (ItemStatModifier modifier : displayedModifiers.stream().limit(4).toList()) {
            String statName = catalogStatName(modifier.statId());
            String amount = switch (modifier.operation()) {
                case FLAT -> (modifier.amount() >= 0 ? "+" : "") + cleanNumber(modifier.amount());
                case PERCENT -> (modifier.amount() >= 0 ? "+" : "") + cleanNumber(modifier.amount() * 100) + "%";
                case MULTIPLIER -> "×" + cleanNumber(modifier.amount());
            };
            draw.text(l.detailX() + 12 * l.scale(), l.detailY() + (79 + row * 14) * l.scale(), amount + " " + statName,
            9.2f * l.scale(), 0xFF9ADBA8, false, false, UiDrawList.TextAlign.LEFT);
            row++;
        }
        if (item.baseDamage() > 0) draw.text(l.detailX() + l.detailW() - 12 * l.scale(), l.detailY() + 78 * l.scale(), "DMG " + cleanNumber(item.baseDamage()), 9.4f * l.scale(), TEXT, true, false, UiDrawList.TextAlign.RIGHT);
        draw.text(l.detailX() + 12 * l.scale(), l.detailY() + l.detailH() - 12 * l.scale(), "SELL VALUE  " + item.sellPrice() + " COINS", 8.7f * l.scale(), MUTED, false, false, UiDrawList.TextAlign.LEFT);
    }

    private void drawStats(UiDrawList.Builder draw, PlayerStats stats, Layout l) {
        draw.text(l.statsX(), l.statsY() - 16 * l.scale(), "PLAYER STATS", 10 * l.scale(), MUTED, true, false, UiDrawList.TextAlign.LEFT);
        List<StatDefinition> visible = stats.definitions().all().stream().filter(StatDefinition::shownInInventory)
        .sorted(Comparator.comparingInt(def -> statOrder(def.id()))).toList();
        int row = 0;
        for (StatDefinition definition : visible) {
            if (row >= 8) break;
            int column = row / 4;
            int line = row % 4;
            float px = l.statsX() + column * 143 * l.scale();
            float py = l.statsY() + line * 17 * l.scale();
            draw.text(px, py, definition.displayName().toUpperCase(Locale.ROOT), 8.4f * l.scale(), MUTED, false, false, UiDrawList.TextAlign.LEFT);
            String value = statFormat(definition.id(), stats.stat(definition.id()));
            draw.text(px + 128 * l.scale(), py, value, 9.3f * l.scale(), TEXT, true, false, UiDrawList.TextAlign.RIGHT);
            row++;
        }
    }

    private void drawHotbar(UiDrawList.Builder draw, InventoryModel inventory, ItemDefinitionRegistry catalog,
    int width, int height, float scale, double mx, double my) {
        float s = scale;
        float size = 42 * s, gap = 5 * s;
        float total = 9 * size + 8 * gap;
        float x0 = (width - total) * 0.5f;
        float y0 = height - 17 * s - size;
        for (int i = 0; i < InventoryModel.HOTBAR_SIZE; i++) {
            SlotAddress slot = new SlotAddress(SlotKind.STORAGE, InventoryModel.HOTBAR_START + i, null, -1,
            x0 + i * (size + gap), y0, size, size);
            drawSlot(draw, slot, inventory, catalog, mx, my, s);
            draw.text(slot.x() + 4 * s, slot.y() + 4 * s, Integer.toString(i + 1), 7.5f * s,
            i == inventory.selectedHotbarIndex() ? ACCENT : SUBTLE, true, false, UiDrawList.TextAlign.LEFT);
        }
        ItemStack selected = inventory.selectedHotbarStack();
        if (selected != null) {
            ItemDefinition item = catalog.require(selected.itemId());
            draw.roundedRect(width * 0.5f - 110 * s, y0 - 23 * s, 220 * s, 17 * s, 0xA0111822, 3 * s);
            draw.text(width * 0.5f, y0 - 20 * s, item.displayName(), 9.0f * s, item.rarity().colorArgb(), true, false, UiDrawList.TextAlign.CENTER);
        }
        lastLayout = lastLayout.withHotbar(slotsForHotbar(x0, y0, size, gap));
    }

    private List<SlotAddress> slotsForHotbar(float x0, float y0, float size, float gap) {
        ArrayList<SlotAddress> slots = new ArrayList<>();
        for (int i = 0; i < InventoryModel.HOTBAR_SIZE; i++) slots.add(new SlotAddress(SlotKind.STORAGE, InventoryModel.HOTBAR_START + i, null, -1,
        x0 + i * (size + gap), y0, size, size));
        return List.copyOf(slots);
    }

    private void drawSlot(UiDrawList.Builder draw, SlotAddress slot, InventoryModel inventory,
    ItemDefinitionRegistry catalog, double mx, double my, float scale) {
        ItemStack stack = stackAt(slot, inventory);
        boolean hovered = contains(slot, mx, my);
        int bg = hovered ? SLOT_HOVER : SLOT;
        int stroke = BORDER;
        if (slot.kind() == SlotKind.EQUIPMENT && slot.equipmentSlot() != ItemEquipmentSlot.ACCESSORY) {
            String label = equipmentShortName(slot.equipmentSlot());
            draw.roundedRect(slot.x(), slot.y(), slot.width(), slot.height(), bg, 3 * scale);
            draw.strokeRoundedRect(slot.x(), slot.y(), slot.width(), slot.height(), hovered ? ACCENT : BORDER, hovered ? 1.5f * scale : 0.8f * scale, 3 * scale);
            if (stack == null) draw.text(slot.x() + slot.width() * 0.5f, slot.y() + slot.height() * 0.5f - 5 * scale,
            label, 8.2f * scale, SUBTLE, true, false, UiDrawList.TextAlign.CENTER);
            else drawItemInSlot(draw, slot, stack, catalog, scale);
            return;
        }
        draw.roundedRect(slot.x(), slot.y(), slot.width(), slot.height(), bg, 3 * scale);
        if (stack != null) stroke = catalog.require(stack.itemId()).rarity().colorArgb();
        if (slot.kind() == SlotKind.STORAGE && slot.index() >= InventoryModel.HOTBAR_START
        && slot.index() - InventoryModel.HOTBAR_START == inventory.selectedHotbarIndex()) stroke = ACCENT;
        draw.strokeRoundedRect(slot.x(), slot.y(), slot.width(), slot.height(), hovered ? 0xFFEAF5FA : stroke,
        slot.kind() == SlotKind.STORAGE && slot.index() >= InventoryModel.HOTBAR_START
        && slot.index() - InventoryModel.HOTBAR_START == inventory.selectedHotbarIndex() ? 1.8f * scale : 0.9f * scale, 3 * scale);
        if (visible && slot.kind() == SlotKind.STORAGE && slot.index() >= InventoryModel.HOTBAR_START) {
            draw.text(slot.x() + 4 * scale, slot.y() + 3 * scale,
            Integer.toString(slot.index() - InventoryModel.HOTBAR_START + 1), 7.5f * scale,
            slot.index() - InventoryModel.HOTBAR_START == inventory.selectedHotbarIndex() ? ACCENT : SUBTLE,
            true, false, UiDrawList.TextAlign.LEFT);
        }
        if (stack != null) drawItemInSlot(draw, slot, stack, catalog, scale);
        else if (slot.kind() == SlotKind.EQUIPMENT) draw.text(slot.x() + slot.width() * 0.5f, slot.y() + slot.height() * 0.5f - 4 * scale,
        "A" + (slot.accessoryIndex() + 1), 8 * scale, SUBTLE, false, false, UiDrawList.TextAlign.CENTER);
    }

    private void drawItemInSlot(UiDrawList.Builder draw, SlotAddress slot, ItemStack stack, ItemDefinitionRegistry catalog, float scale) {
        ItemDefinition item = catalog.require(stack.itemId());
        boolean drewImage = false;
        if (itemVisuals != null) {
            var icon = itemVisuals.icon(item.id());
            if (icon.isPresent()) {
                float margin = Math.max(3.0f, 4.0f * scale);
                float iconSize = Math.max(1.0f, Math.min(slot.width(), slot.height()) - margin * 2.0f);
                draw.image(slot.x() + (slot.width() - iconSize) * 0.5f,
                slot.y() + (slot.height() - iconSize) * 0.5f, iconSize, iconSize, icon.get());
                drewImage = true;
            }
        }
        if (!drewImage) draw.text(slot.x() + slot.width() * 0.5f, slot.y() + slot.height() * 0.5f - 5.5f * scale,
        item.icon(), Math.min(13.0f, 13.0f * scale), item.rarity().colorArgb(), true, false, UiDrawList.TextAlign.CENTER);
        if (stack.amount() > 1) draw.text(slot.x() + slot.width() - 3 * scale, slot.y() + slot.height() - 14 * scale,
        stack.amount() > 999 ? "999+" : Integer.toString(stack.amount()), 8.5f * scale, TEXT, true, true, UiDrawList.TextAlign.RIGHT);
    }

    private void drawSlotAtCursor(UiDrawList.Builder draw, ItemStack stack, ItemDefinitionRegistry catalog, double x, double y, float scale) {
        ItemDefinition definition = catalog.require(stack.itemId());
        float size = 40 * scale;
        draw.roundedRect((float) x + 7 * scale, (float) y + 7 * scale, size, size, 0xE6202A36, 3 * scale);
        draw.strokeRoundedRect((float) x + 7 * scale, (float) y + 7 * scale, size, size, definition.rarity().colorArgb(), 1 * scale, 3 * scale);
        boolean drewImage = false;
        if (itemVisuals != null) {
            var icon = itemVisuals.icon(definition.id());
            if (icon.isPresent()) {
                draw.image((float) x + 7 * scale + 2 * scale, (float) y + 7 * scale + 2 * scale,
                size - 4 * scale, size - 4 * scale, icon.get());
                drewImage = true;
            }
        }
        if (!drewImage) draw.text((float) x + 7 * scale + size * 0.5f, (float) y + 7 * scale + size * 0.5f - 5 * scale,
        definition.icon(), 13 * scale, definition.rarity().colorArgb(), true, false, UiDrawList.TextAlign.CENTER);
        if (stack.amount() > 1) draw.text((float) x + 7 * scale + size - 2 * scale, (float) y + 7 * scale + size - 13 * scale,
        Integer.toString(stack.amount()), 8 * scale, TEXT, true, false, UiDrawList.TextAlign.RIGHT);
    }

    private void leftClick(SlotAddress address, InventoryModel inventory, ItemDefinitionRegistry catalog) {
        ItemStack carried = inventory.cursorStack();
        ItemStack target = stackAt(address, inventory);
        if (carried == null) {
            if (target == null) return;
            setStack(address, null, inventory, catalog);
            inventory.setCursorStack(target, catalog);
            return;
        }
        if (address.kind() == SlotKind.EQUIPMENT && !isCompatible(carried, address, catalog)) return;
        if (target == null) {
            setStack(address, carried, inventory, catalog);
            inventory.clearCursorStack();
            return;
        }
        if (address.kind() == SlotKind.STORAGE && target.canStackWith(carried)) {
            int max = catalog.require(carried.itemId()).maxStackSize();
            int moved = Math.min(carried.amount(), max - target.amount());
            if (moved > 0) {
                setStack(address, new ItemStack(target.itemId(), target.amount() + moved), inventory, catalog);
                inventory.setCursorStack(carried.withAmount(carried.amount() - moved), catalog);
                return;
            }
        }
        if (address.kind() == SlotKind.EQUIPMENT && !isCompatible(carried, address, catalog)) return;
        setStack(address, carried, inventory, catalog);
        inventory.setCursorStack(target, catalog);
    }

    private void rightClick(SlotAddress address, InventoryModel inventory, ItemDefinitionRegistry catalog) {
        if (address.kind() != SlotKind.STORAGE) return;
        ItemStack target = stackAt(address, inventory);
        ItemStack carried = inventory.cursorStack();
        if (carried == null) {
            if (target == null) return;
            int take = (target.amount() + 1) / 2;
            if (target.hasInstanceState() || target.amount() == 1) {
                setStack(address, null, inventory, catalog);
                inventory.setCursorStack(target, catalog);
            } else {
                setStack(address, target.withAmount(target.amount() - take), inventory, catalog);
                inventory.setCursorStack(target.withAmount(take), catalog);
            }
            return;
        }
        if (target == null) {
            if (carried.hasInstanceState() || carried.amount() == 1) {
                setStack(address, carried, inventory, catalog);
                inventory.clearCursorStack();
            } else {
                setStack(address, new ItemStack(carried.itemId(), 1), inventory, catalog);
                inventory.setCursorStack(carried.withAmount(carried.amount() - 1), catalog);
            }
            return;
        }
        if (target.canStackWith(carried)) {
            int max = catalog.require(target.itemId()).maxStackSize();
            if (target.amount() < max) {
                setStack(address, new ItemStack(target.itemId(), target.amount() + 1), inventory, catalog);
                inventory.setCursorStack(carried.withAmount(carried.amount() - 1), catalog);
            }
        }
    }

    private void quickMove(int sourceIndex, InventoryModel inventory, ItemDefinitionRegistry catalog) {
        ItemStack source = inventory.storage(sourceIndex);
        if (source == null) return;
        ItemDefinition definition = catalog.require(source.itemId());
        if (definition.equippable()) {
            SlotAddress destination = nextEquipmentSlot(definition.equipmentSlot(), inventory);
            if (destination != null && stackAt(destination, inventory) == null) {
                setStack(destination, source, inventory, catalog);
                inventory.setStorage(sourceIndex, null, catalog);
                return;
            }
        }
        int start = sourceIndex < InventoryModel.HOTBAR_START ? InventoryModel.HOTBAR_START : 0;
        int end = sourceIndex < InventoryModel.HOTBAR_START ? InventoryModel.STORAGE_SIZE : InventoryModel.HOTBAR_START;
        if (source.hasInstanceState()) {
            for (int i = start; i < end; i++) {
                if (inventory.storage(i) == null) {
                    inventory.setStorage(i, source, catalog);
                    inventory.setStorage(sourceIndex, null, catalog);
                    return;
                }
            }
            return;
        }
        int remaining = source.amount();
        for (int i = start; i < end && remaining > 0; i++) {
            ItemStack target = inventory.storage(i);
            if (target == null || !target.canStackWith(source)) continue;
            int max = catalog.require(target.itemId()).maxStackSize();
            int moved = Math.min(remaining, max - target.amount());
            if (moved > 0) {
                inventory.setStorage(i, new ItemStack(target.itemId(), target.amount() + moved), catalog);
                remaining -= moved;
            }
        }
        for (int i = start; i < end && remaining > 0; i++) {
            if (inventory.storage(i) != null) continue;
            int moved = Math.min(remaining, definition.maxStackSize());
            inventory.setStorage(i, new ItemStack(source.itemId(), moved), catalog);
            remaining -= moved;
        }
        inventory.setStorage(sourceIndex, remaining == 0 ? null : source.withAmount(remaining), catalog);
    }

    private SlotAddress nextEquipmentSlot(ItemEquipmentSlot slot, InventoryModel inventory) {
        if (slot == ItemEquipmentSlot.NONE) return null;
        if (slot == ItemEquipmentSlot.ACCESSORY) {
            for (int i = 0; i < InventoryModel.ACCESSORY_SLOTS; i++) {
                if (inventory.accessory(i) == null) {
                    final int accessoryIndex = i;
                    return lastLayout.equipmentSlots().stream().filter(a -> a.kind() == SlotKind.EQUIPMENT && a.equipmentSlot() == slot && a.accessoryIndex() == accessoryIndex).findFirst().orElse(null);
                }
            }
            return null;
        }
        if (inventory.equipped(slot) != null) return null;
        return lastLayout.equipmentSlots().stream().filter(a -> a.kind() == SlotKind.EQUIPMENT && a.equipmentSlot() == slot).findFirst().orElse(null);
    }

    private void setStack(SlotAddress address, ItemStack stack, InventoryModel inventory, ItemDefinitionRegistry catalog) {
        if (address.kind() == SlotKind.STORAGE) inventory.setStorage(address.index(), stack, catalog);
        else inventory.setEquipped(address.equipmentSlot(), address.accessoryIndex(), stack, catalog);
    }

    private ItemStack stackAt(SlotAddress address, InventoryModel inventory) {
        if (address.kind() == SlotKind.STORAGE) return inventory.storage(address.index());
        return inventory.equipmentAt(address.equipmentSlot(), address.accessoryIndex());
    }

    private boolean isCompatible(ItemStack stack, SlotAddress address, ItemDefinitionRegistry catalog) {
        if (address.kind() != SlotKind.EQUIPMENT) return true;
        ItemDefinition definition = catalog.require(stack.itemId());
        return definition.equipmentSlot() != ItemEquipmentSlot.NONE && definition.equipmentSlot() == address.equipmentSlot();
    }

    private ItemStack hoveredStack(InventoryModel inventory, Layout layout) {
        SlotAddress address = hit(layout.inventorySlots(), mouseX, mouseY);
        if (address == null) address = hit(layout.equipmentSlots(), mouseX, mouseY);
        return address == null ? null : stackAt(address, inventory);
    }

    private Layout layout(int width, int height) {
        float scale = Math.min(width / 1280.0f, height / 720.0f);
        scale = Math.max(0.55f, Math.min(1.5f, scale));
        float w = 850 * scale, h = 560 * scale;
        float x = (width - w) * 0.5f, y = (height - h) * 0.5f;
        float invX = x + 23 * scale, invY = y + 119 * scale;
        float size = 42 * scale, gap = 5 * scale;
        ArrayList<SlotAddress> storageSlots = new ArrayList<>();
        for (int i = 0; i < InventoryModel.STORAGE_SIZE; i++) {
            int row = i < InventoryModel.MAIN_STORAGE_SIZE ? i / 9 : 3;
            int col = i < InventoryModel.MAIN_STORAGE_SIZE ? i % 9 : i - InventoryModel.HOTBAR_START;
            float sy = invY + row * (size + gap) + (row == 3 ? 10 * scale : 0);
            storageSlots.add(new SlotAddress(SlotKind.STORAGE, i, null, -1, invX + col * (size + gap), sy, size, size));
        }
        float equipX = x + 505 * scale, equipY = y + 125 * scale;
        float eSize = 57 * scale, eGap = 12 * scale, rowGap = 8 * scale;
        ArrayList<SlotAddress> equipSlots = new ArrayList<>();
        addEquipmentSlot(equipSlots, ItemEquipmentSlot.WEAPON, 0, equipX, equipY, eSize);
        addEquipmentSlot(equipSlots, ItemEquipmentSlot.PET, 0, equipX + eSize + eGap, equipY, eSize);
        addEquipmentSlot(equipSlots, ItemEquipmentSlot.HELMET, 0, equipX, equipY + eSize + rowGap, eSize);
        addEquipmentSlot(equipSlots, ItemEquipmentSlot.CHESTPLATE, 0, equipX + eSize + eGap, equipY + eSize + rowGap, eSize);
        addEquipmentSlot(equipSlots, ItemEquipmentSlot.LEGGINGS, 0, equipX, equipY + 2 * (eSize + rowGap), eSize);
        addEquipmentSlot(equipSlots, ItemEquipmentSlot.BOOTS, 0, equipX + eSize + eGap, equipY + 2 * (eSize + rowGap), eSize);
        float accessorySize = 34 * scale, accessoryGap = 5 * scale;
        float accessoryY = equipY + 3 * (eSize + rowGap) + 2 * scale;
        for (int i = 0; i < InventoryModel.ACCESSORY_SLOTS; i++) {
            equipSlots.add(new SlotAddress(SlotKind.EQUIPMENT, -1, ItemEquipmentSlot.ACCESSORY, i,
            equipX + i * (accessorySize + accessoryGap), accessoryY, accessorySize, accessorySize));
        }
        float detailX = invX, detailY = y + 350 * scale, detailW = 9 * size + 8 * gap, detailH = 155 * scale;
        float statsX = equipX, statsY = y + 410 * scale;
        return new Layout(x, y, scale, w, h, invX, invY, equipX, List.copyOf(storageSlots), List.copyOf(equipSlots),
        detailX, detailY, detailW, detailH, statsX, statsY, equipY, slotsForHotbar((width - (9 * size + 8 * gap)) * 0.5f, height - 17 * scale - size, size, gap));
    }

    private static void addEquipmentSlot(List<SlotAddress> out, ItemEquipmentSlot slot, int accessoryIndex, float x, float y, float size) {
        out.add(new SlotAddress(SlotKind.EQUIPMENT, -1, slot, accessoryIndex, x, y, size, size));
    }
    private static SlotAddress hit(List<SlotAddress> slots, double x, double y) {
        for (SlotAddress slot : slots) if (contains(slot, x, y)) return slot;
        return null;
    }
    private static boolean contains(SlotAddress slot, double x, double y) {
        return x >= slot.x() && y >= slot.y() && x < slot.x() + slot.width() && y < slot.y() + slot.height();
    }
    private static String equipmentShortName(ItemEquipmentSlot slot) {
        return switch (slot) { case WEAPON -> "WEAPON"; case HELMET -> "HEAD"; case CHESTPLATE -> "CHEST"; case LEGGINGS -> "LEGS"; case BOOTS -> "BOOTS"; case PET -> "PET"; default -> ""; };
    }
    private static int statOrder(String id) {
        return switch (id) { case "max_health" -> 0; case "max_mana" -> 1; case "defense" -> 2; case "strength" -> 3; case "intelligence" -> 4; case "crit_chance" -> 5; case "crit_damage" -> 6; case "movement_speed" -> 7; default -> 100; };
    }
    private static String statFormat(String id, float value) {
        if (id.equals("crit_chance") || id.equals("crit_damage") || id.equals("movement_speed") || id.equals("damage_multiplier")) {
            return Math.round(value * 100) + "%";
        }
        if (Math.abs(value - Math.rint(value)) < 0.005) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
    }
    private static String catalogStatName(String id) { return id.replace('_', ' ').toUpperCase(Locale.ROOT); }
    private static String cleanNumber(float value) { return Math.abs(value - Math.rint(value)) < 0.005 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value); }
    private static String trim(String text, int max) { return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…"; }
    private static Key numberKey(int index) { return switch (index) { case 0 -> Key.NUMBER_1; case 1 -> Key.NUMBER_2; case 2 -> Key.NUMBER_3; case 3 -> Key.NUMBER_4; case 4 -> Key.NUMBER_5; case 5 -> Key.NUMBER_6; case 6 -> Key.NUMBER_7; case 7 -> Key.NUMBER_8; default -> Key.NUMBER_9; }; }
    private enum SlotKind { STORAGE, EQUIPMENT }
    private record SlotAddress(SlotKind kind, int index, ItemEquipmentSlot equipmentSlot, int accessoryIndex,
    float x, float y, float width, float height) { }
    private record Layout(float x, float y, float scale, float width, float height, float invX, float invY,
    float equipX, List<SlotAddress> inventorySlots, List<SlotAddress> equipmentSlots,
    float detailX, float detailY, float detailW, float detailH, float statsX, float statsY,
    float equipY, List<SlotAddress> hotbarSlots) {
        Layout(float x, float y, float scale, float width, float height, float invX, float invY, float equipX,
        List<SlotAddress> inventorySlots, List<SlotAddress> equipmentSlots) {
            this(x,y,scale,width,height,invX,invY,equipX,inventorySlots,equipmentSlots,0,0,0,0,0,0,0,List.of());
        }
        Layout withHotbar(List<SlotAddress> hotbar) {
            return new Layout(x,y,scale,width,height,invX,invY,equipX,inventorySlots,equipmentSlots,detailX,detailY,detailW,detailH,statsX,statsY,equipY,hotbar);
        }
    }
}