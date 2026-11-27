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
        // The specks a carried aspect leaves behind the cursor: glowing, rising a little, fading.
        DiegesisClient.registerParticle(ResearchBehavior.SPECK, com.pockyl.diegesis.api.client.ParticleStyle
                .builder(com.pockyl.diegesis.api.client.ParticleStyle.frames("minecraft:generic", 8))
                .size(0.45F, 0.8F).life(0.45F, 0.8F).gravity(-1.5F).drag(0.92F).wobble(0.6F).emissive().build());
        DiegesisClient.registerBehavior(net.handsonthaumaturge.station.ResearchStation.ID, ResearchBehavior::new);
        DiegesisClient.registerEffect(WorkbenchStation.EFFECT_CRAFTED, (view, node, data) -> {
        });
    }
}
