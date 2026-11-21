package net.handsonthaumaturge.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

/** The research table at work: its two slots bound, the note's puzzle drawn when a note is laid in, the sheet's actions done. */
public final class ResearchLogic implements StationLogic {
    private final BlockEntity table;
    private final IItemHandlerModifiable items;

    public ResearchLogic(StationContext station) {
        // Either half opens the station; the note and the tools are the main half's.
        this.table = Thaum.researchTableAt(station.level(), station.pos());
        this.items = table == null ? null : Thaum.tableItems(table);
        if (items == null) {
            return;
        }
        station.state().bindSlots(ResearchStation.TOOLS, slots(0));
        station.state().bindSlots(ResearchStation.NOTE, slots(1));
    }

    private SlotStorage slots(int slot) {
        return new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                return items.getStackInSlot(slot);
            }

            @Override
            public void set(int index, ItemStack stack) {
                items.setStackInSlot(slot, stack);
            }
        };
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return items != null && items.isItemValid(ResearchStation.TOOLS.equals(slot.group()) ? 0 : 1, stack);
    }

    @Override
    public void onSlotChanged(StationContext station, SlotRef slot) {
        if (table != null && !station.isClientSide()) {
            Thaum.notesChanged(table);
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (table == null || !(player instanceof ServerPlayer server)) {
            return;
        }
        switch (action) {
            case ResearchStation.ACTION_PLACE -> Thaum.place(table, server, data.getInt(ResearchStation.Q), data.getInt(ResearchStation.R),
                    data.contains(ResearchStation.ASPECT) ? ResourceLocation.tryParse(data.getString(ResearchStation.ASPECT)) : null);
            case ResearchStation.ACTION_COMBINE -> Thaum.combine(table, server, ResourceLocation.tryParse(data.getString(ResearchStation.FIRST)),
                    ResourceLocation.tryParse(data.getString(ResearchStation.SECOND)), data.getBoolean(ResearchStation.BONUS_FIRST),
                    data.getBoolean(ResearchStation.BONUS_SECOND));
            case ResearchStation.ACTION_COPY -> Thaum.duplicate(table, server);
            default -> {
            }
        }
    }
}
