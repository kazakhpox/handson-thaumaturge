package net.handsonthaumaturge.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

/**
 * The arcane workbench at work. The grid, the crystals and the wand are the workbench's own slots; the result is what
 * Thaumaturge's own workbench menu shows for the player at it, and taking it crafts through that menu, so recipes, research
 * gates, wand and crystal payment and the aura's vis are all Thaumaturge's. The clients hear the vis round the workbench, what
 * the recipe costs and which crystals it burns, for the sigils under the crystals.
 */
public final class WorkbenchLogic implements StationLogic {
    private static final int GRID_START = 0;
    private static final int CRYSTAL_START = 9;
    private static final int WAND_SLOT = 15;
    /** Thaumaturge's menu reads the aura every half second. */
    private static final int AURA_EVERY = 10;

    private final BlockEntity workbench;
    private final Container container;
    private int vis = -1;
    private int age;

    public WorkbenchLogic(StationContext station) {
        BlockEntity entity = station.blockEntity();
        this.workbench = Thaum.isWorkbench(entity) ? entity : null;
        this.container = workbench == null ? null : Thaum.inventory(workbench);
        if (container == null) {
            return;
        }
        station.state().bindSlots(WorkbenchStation.GRID, SlotStorage.of(container, GRID_START, 9));
        station.state().bindSlots(WorkbenchStation.CRYSTALS, SlotStorage.of(container, CRYSTAL_START, 6));
        station.state().bindSlots(WorkbenchStation.WAND, SlotStorage.of(container, WAND_SLOT, 1));
    }

    private static int index(SlotRef slot) {
        return switch (slot.group()) {
            case WorkbenchStation.CRYSTALS -> CRYSTAL_START + slot.index();
            case WorkbenchStation.WAND -> WAND_SLOT;
            default -> GRID_START + slot.index();
        };
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        if (container == null) {
            return false;
        }
        if (WorkbenchStation.CRYSTALS.equals(slot.group())) {
            // Each crystal slot takes its own primal's essentia crystal, as the screen's slots do.
            return Thaum.isCrystal(stack, slot.index());
        }
        return container.canPlaceItem(index(slot), stack);
    }

    private ServerPlayer worker(StationContext station) {
        for (Player player : station.users()) {
            if (player instanceof ServerPlayer server) {
                return server;
            }
        }
        return null;
    }

    @Override
    public void updateOutputs(StationContext station) {
        if (container == null || station.isClientSide()) {
            return;
        }
        ServerPlayer player = worker(station);
        ItemStack result = ItemStack.EMPTY;
        Thaum.Cost cost = Thaum.Cost.NONE;
        if (player != null) {
            AbstractContainerMenu menu = Thaum.openMenu(player, workbench);
            result = menu.getSlot(0).getItem().copy();
            Thaum.close(menu, player);
            cost = Thaum.cost(player, workbench);
        }
        station.state().setOutput(WorkbenchStation.RESULT, result);
        CompoundTag data = station.state().data().copy();
        if (data.getInt(WorkbenchStation.COST) != cost.vis() || data.getInt(WorkbenchStation.BURNS) != cost.crystals()
                || !data.contains(WorkbenchStation.COST)) {
            data.putInt(WorkbenchStation.COST, cost.vis());
            data.putInt(WorkbenchStation.BURNS, cost.crystals());
            station.state().setData(data);
        }
    }

    @Override
    public ItemStack takeOutput(StationContext station, Player player, String output) {
        if (container == null || !(player instanceof ServerPlayer server)) {
            return ItemStack.EMPTY;
        }
        AbstractContainerMenu menu = Thaum.openMenu(server, workbench);
        try {
            if (menu.getSlot(0).getItem().isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack crafted = Thaum.craft(menu);
            if (!crafted.isEmpty()) {
                station.effect(WorkbenchStation.EFFECT_CRAFTED, "result");
            }
            return crafted;
        } finally {
            Thaum.close(menu, server);
        }
    }

    @Override
    public void tick(StationContext station) {
        if (container == null || station.isClientSide() || age++ % AURA_EVERY != 0) {
            return;
        }
        int now = Thaum.refreshAura(workbench);
        if (now != vis) {
            vis = now;
            CompoundTag data = station.state().data().copy();
            data.putInt(WorkbenchStation.VIS, now);
            station.state().setData(data);
            // The aura changed: whether the recipe can be paid may have too.
            updateOutputs(station);
        }
    }

    @Override
    public void onOpen(StationContext station, Player player) {
        updateOutputs(station);
    }
}
