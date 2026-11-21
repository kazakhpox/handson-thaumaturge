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
 * Thaumaturge's research table as a station. Its scribing tools and note are windows on the table's own two slots; what is done
 * on the note comes as actions the logic hands to the table's own methods (the ones its screen's packets reach), so ink, aspect
 * costs, aids, mastery and copying are all Thaumaturge's.
 */
public final class ResearchStation {
    public static final ResourceLocation ID = HandsOnThaumaturge.id("research_table");
    public static final String TOOLS = "tools";
    public static final String NOTE = "note";
    /** Actions: write an aspect into a hex ({@link #Q}, {@link #R}, {@link #ASPECT}; no aspect rubs out), mix two, copy the note. */
    public static final String ACTION_PLACE = "place";
    public static final String ACTION_COMBINE = "combine";
    public static final String ACTION_COPY = "copy";
    public static final String Q = "q";
    public static final String R = "r";
    public static final String ASPECT = "aspect";
    public static final String FIRST = "first";
    public static final String SECOND = "second";
    public static final String BONUS_FIRST = "bonus_first";
    public static final String BONUS_SECOND = "bonus_second";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(TOOLS, 1, SlotRules.DEFAULT.kept().maxStack(1))
            .slots(NOTE, 1, SlotRules.DEFAULT.kept())
            .build();

    public static final StationType TYPE = Diegesis.station(ID)
            .block(Thaum::researchTable)
            .schema(SCHEMA)
            .logic(ResearchLogic::new)
            .openWhen(ResearchStation::opens)
            .orientation(StationType.Orientation.PLAYER)
            .build();

    private ResearchStation() {
    }

    public static void register() {
        Diegesis.register(TYPE);
    }

    /** Sneaking keeps Thaumaturge's screen; either half of the table opens the station. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive();
    }
}
