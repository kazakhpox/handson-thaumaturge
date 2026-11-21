package net.handsonthaumaturge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.pockyl.diegesis.api.client.DiegesisClient;

import net.handsonthaumaturge.HandsOnThaumaturge;
import net.handsonthaumaturge.station.WorkbenchStation;

/** Client setup: the workbench's behaviour, the research table's, and its effect (the behaviour plays it, see {@link WorkbenchBehavior#onEffect}). */
@Mod(value = HandsOnThaumaturge.MOD_ID, dist = Dist.CLIENT)
public final class HandsOnThaumaturgeClient {
    public HandsOnThaumaturgeClient(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(HandsOnThaumaturgeClient::setup));
    }

    private static void setup() {
        DiegesisClient.registerBehavior(WorkbenchStation.ID, WorkbenchBehavior::new);
        DiegesisClient.registerBehavior(net.handsonthaumaturge.station.ResearchStation.ID, ResearchBehavior::new);
        DiegesisClient.registerEffect(WorkbenchStation.EFFECT_CRAFTED, (view, node, data) -> {
        });
    }
}
