package net.handsonthaumaturge.station;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;

import net.handsonthaumaturge.HandsOnThaumaturge;

/**
 * Thaumaturge's arcane workbench as a station. Its slot groups are windows on the workbench's own inventory, so hoppers, the
 * block's own screen (sneaking) and what is left on it all see the same items; the result is crafted by Thaumaturge's own menu.
 */
public final class WorkbenchStation {
    public static final ResourceLocation ID = HandsOnThaumaturge.id("arcane_workbench");
    public static final String GRID = "grid";
    public static final String CRYSTALS = "crystals";
    public static final String WAND = "wand";
    public static final String RESULT = "result";
    /** Data: the vis in the aura round the workbench; what the recipe on the grid costs (-1: no arcane recipe); the crystals it burns. */
    public static final String VIS = "vis";
    public static final String COST = "cost";
    public static final String BURNS = "burns";
    public static final ResourceLocation EFFECT_CRAFTED = HandsOnThaumaturge.id("crafted");

    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> WANDS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("thaumaturge", "wands"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            // The order a shift-click from the inventory tries them in: a wand to the wand's place, a crystal to its primal's
            // (the logic checks which), anything else to the grid.
            .slots(WAND, 1, SlotRules.DEFAULT.kept().maxStack(1).accepts(stack -> stack.is(WANDS)))
            .slots(CRYSTALS, 6, SlotRules.DEFAULT.kept())
            .slots(GRID, 9, SlotRules.DEFAULT.kept())
            .output(RESULT)
            .build();

    public static final StationType TYPE = Diegesis.station(ID)
            .block(Thaum::workbench)
            .schema(SCHEMA)
            .logic(WorkbenchLogic::new)
            .openWhen(WorkbenchStation::opens)
            .build();

    private WorkbenchStation() {
    }

    public static void register() {
        Diegesis.register(TYPE);
    }

    /** Sneaking keeps Thaumaturge's screen; and only a player who has the research for it understands the workbench at all. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && (player.level().isClientSide() || Thaum.understands(player));
    }
}
