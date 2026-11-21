package dev.galacticshaders;

import micdoodle8.mods.galacticraft.core.dimension.WorldProviderOrbit;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import java.util.Locale;
import net.coderbot.iris.Iris;

/** Stable shader identity for stations whose actual dimension IDs are allocated by the server. */
public final class StationLighting {
    public static String dimensionName(String original) {
        String pack = Iris.getCurrentPackName();
        if (pack == null || !pack.startsWith("GalacticShaders-0.3-")) return original;
        World world = FMLClientHandler.instance().getWorldClient();
        if (world == null) return original;
        WorldProvider provider = ReflectionHelper.getPrivateValue(World.class, world, "provider", "field_73011_w");
        if (!(provider instanceof WorldProviderOrbit)) return original;
        WorldProviderOrbit orbit = (WorldProviderOrbit) provider;
        return environment(orbit.getPlanetToOrbit()).folder();
    }

    static Environment environment(String parent) {
        if (parent == null) return Environment.STATION_EARTH;
        String key = parent.substring(parent.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        try {
            return Environment.valueOf("STATION_" + key);
        } catch (IllegalArgumentException unknownParent) {
            return Environment.STATION_EARTH;
        }
    }
}
