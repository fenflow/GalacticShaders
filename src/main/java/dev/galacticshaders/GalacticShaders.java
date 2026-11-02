package dev.galacticshaders;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import micdoodle8.mods.galacticraft.api.galaxies.*;
import java.nio.file.Path;
import java.util.*;
import java.io.IOException;

@Mod(modid="galacticshaders", name="GalacticShaders", version="0.3.4-alpha",
     acceptableRemoteVersions="*", dependencies="required-after:GalacticraftCore;required-after:angelica;after:GalaxySpace")
public final class GalacticShaders {
    private Path packs;
    @Mod.EventHandler public void configure(FMLPreInitializationEvent event) {
        if (event.getSide().isClient()) packs = event.getModConfigurationDirectory().toPath().resolveSibling("shaderpacks");
    }

    @Mod.EventHandler public void generate(FMLLoadCompleteEvent event) {
        if (packs == null) return;
        Map<Environment,Set<String>> names = new EnumMap<>(Environment.class);
        List<CelestialBody> bodies = new ArrayList<>(GalaxyRegistry.getRegisteredPlanets().values());
        bodies.addAll(GalaxyRegistry.getRegisteredMoons().values());
        for (CelestialBody body : bodies) {
            int id = body.getDimensionID();
            if (!body.getReachable() || id == 0 || id == -1 || id == 1) continue;
            names.computeIfAbsent(Environment.of(body), key -> new TreeSet<>()).add(body.getLocalizedName());
            java.util.logging.Logger.getLogger("GalacticShaders").info("Planet " + body.getName()
                + " | " + body.getLocalizedName() + " | dimension " + id + " | " + Environment.of(body));
        }
        for (String archive : new String[]{"BSL_v10.1.8.zip","ComplementaryReimagined_r5.9.3.zip","photon_v1.4.zip"}) {
            try { PackAdapter.generate(packs, archive, names); }
            catch (IOException e) { java.util.logging.Logger.getLogger("GalacticShaders").log(
                java.util.logging.Level.WARNING, "Could not generate " + archive, e); }
        }
    }
}
