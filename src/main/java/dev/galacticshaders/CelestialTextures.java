package dev.galacticshaders;

import net.coderbot.iris.Iris;
import net.minecraft.util.ResourceLocation;

/** Let adapted packs draw their solar disk without GalaxySpace's second oversized sun/halo. */
public final class CelestialTextures {
    private static final ResourceLocation EMPTY = new ResourceLocation("galacticshaders", "textures/empty.png");
    private static ResourceLocation currentSolarTexture;
    public static double texturedQuadY(ResourceLocation texture) {
        return texture.equals(currentSolarTexture) ? -100.03125 : -100.0;
    }
    public static ResourceLocation sun(ResourceLocation original) {
        String pack = Iris.getCurrentPackName();
        currentSolarTexture=pack!=null && pack.startsWith("GalacticShaders-0.3-Complementary") ? original : null;
        return Iris.getCurrentPack().isPresent() && pack != null && pack.startsWith("GalacticShaders-0.3-")
            && !pack.startsWith("GalacticShaders-0.3-Complementary") ? EMPTY : original;
    }
}
