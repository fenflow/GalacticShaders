package dev.galacticshaders;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;

public final class PlanetUniform {
    public static void register(UniformHolder uniforms) {
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "galacticDimension", Iris::getCurrentDimensionId);
    }
}
