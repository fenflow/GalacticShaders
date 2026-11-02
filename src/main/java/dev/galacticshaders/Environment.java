package dev.galacticshaders;

import java.util.Locale;
import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.galaxies.Moon;

/** Relative direct solar irradiance, diffuse sky fill and atmospheric extinction per block. */
enum Environment {
    MOON(1,1.0,0,0,"0.0"),
    MERCURY(1,6.6,0,0,"0.0"),
    MARTIAN_MOON(1,0.43,0,0,"0.0"),
    ASTEROIDS(1,0.13,0,0,"0.0"),
    OUTER_VACUUM(1,0.037,0,0,"0.0"),
    SATURNIAN_MOON(1,0.011,0,0,"0.0"),
    DISTANT_VACUUM(1,0.002,0,0,"0.0"),
    NEPTUNIAN_MOON(1,0.0011,0,0,"0.0"),
    KUIPER(1,0.0006,0,0,"0.0"),
    CENTAURI_BB(2,0.12,0.55,0.006,"0.75,0.49,0.25","1.0,0.82,0.62"),
    BARNARDA_C(4,0.8,0.24,0.0012,"0.24,0.34,0.69","1.0,0.65,0.43"),
    BARNARDA_E(2,0.08,0.18,0.003,"0.50,0.25,0.13","1.0,0.65,0.43"),
    BARNARDA_F(1,0.6,0,0,"0.0","1.0,0.65,0.43"),
    VEGA_B(1,1.1,0,0,"0.0","0.79,0.87,1.0"),
    TAU_CETI_E(4,0.8,0.26,0.0018,"0.26,0.67,1.0","1.0,0.91,0.76"),
    ROSS_B(4,1.38,0.25,0.0015,"0.78,0.47,0.12","1.0,0.66,0.45"),
    ROSS_BA(1,1.9,0,0,"0.0","1.0,0.66,0.45"),
    HORUS(1,3.3,0,0,"0.0","1.0,0.75,0.63"),
    NEPER(4,0.7,0.3,0.0015,"0.5,0.75,1.0","1.0,0.75,0.63"),
    MAAHES(4,0.39,0.22,0.002,"0.43,0.77,0.65","1.0,0.75,0.63"),
    SETH(1,0.39,0,0,"0.0","1.0,0.75,0.63"),
    ANUBIS(1,0.28,0,0,"0.0","1.0,0.75,0.63"),
    MEHEN(1,0.51,0,0,"0.0","1.0,0.75,0.63"),
    VENUS(2,0.065,0.75,0.008,"0.93,0.56,0.23"),
    MARS(3,0.43,0.12,0.0009,"0.64,0.31,0.16"),
    TITAN(2,0.002,0.02,0.005,"0.73,0.38,0.12"),
    ATMOSPHERE(4,1.0,0.32,0.002,"0.32,0.49,0.65");

    final boolean vacuum;
    final String defines;
    Environment(int kind, double sun, double diffuse, double extinction, String color) {
        this(kind,sun,diffuse,extinction,color,"1.0,0.98,0.94");
    }
    Environment(int kind, double sun, double diffuse, double extinction, String color, String lightColor) {
        vacuum = kind == 1;
        boolean outer=kind==1 && sun<=0.037;
        double terrain=(outer || name().equals("TITAN")) ? 0.12 + 0.32*Math.sqrt(sun) : sun;
        defines = (name().equals("ASTEROIDS") ? "#define GS_ASTEROIDS\n" : "") + "#define GS_TERRAIN_SUN " + terrain + "\n#define GS_DAY_STARS " + (outer ? "0.62" : "0.0") + "\n" + "#define GS_PLANET " + kind + "\n#define GS_SUN " + sun + "\n#define GS_FILL " + diffuse
            + "\n#define GS_EXTINCTION " + extinction + "\n#define GS_TINT vec3(" + color + ")\n"
            + "#define GS_LIGHT_COLOR vec3(" + lightColor + ")\n#define GS_STARS " + (kind == 2 ? "0.0" : "1.0") + "\n";
        // Amun-Ra and BartWorks supply their own star textures, including Amun-Ra's companion star.
    }
    static Environment of(CelestialBody body) {
        String name = body.getName().toLowerCase(Locale.ROOT);
        if ("venus".equals(name)) return VENUS;
        if ("titan".equals(name)) return TITAN;
        if ("mars".equals(name)) return MARS;
        if ("mercury".equals(name)) return MERCURY;
        if ("moon".equals(name)) return MOON;
        if ("centauribb".equals(name)) return CENTAURI_BB;
        if ("barnarda2".equals(name)) return BARNARDA_C;
        if ("barnarda4".equals(name)) return BARNARDA_E;
        if ("barnarda5".equals(name)) return BARNARDA_F;
        if ("vega1".equals(name)) return VEGA_B;
        if ("tcetie".equals(name)) return TAU_CETI_E;
        if ("ross128b".equals(name)) return ROSS_B;
        if ("ross128ba".equals(name)) return ROSS_BA;
        if ("horus".equals(name)) return HORUS;
        if ("neper".equals(name)) return NEPER;
        if ("maahes".equals(name)) return MAAHES;
        if ("seth".equals(name)) return SETH;
        if ("anubis".equals(name)) return ANUBIS;
        if ("asteroidbeltmehen".equals(name)) return MEHEN;
        if ("ceres".equals(name) || "asteroids".equals(name)) return ASTEROIDS;
        if ("pluto".equals(name) || "haumea".equals(name) || "makemake".equals(name)
            || "kuiperbelt".equals(name) || "eris".equals(name)) return KUIPER;
        if (!body.atmosphere.isEmpty()) return ATMOSPHERE;
        CelestialBody parent = body instanceof Moon ? ((Moon)body).getParentPlanet() : body;
        if (parent != null) {
            String host = parent.getName().toLowerCase(Locale.ROOT);
            if ("mars".equals(host)) return MARTIAN_MOON;
            if ("jupiter".equals(host)) return OUTER_VACUUM;
            if ("saturn".equals(host)) return SATURNIAN_MOON;
            if ("uranus".equals(host)) return DISTANT_VACUUM;
            if ("neptune".equals(host)) return NEPTUNIAN_MOON;
            if ("pluto".equals(host)) return KUIPER;
        }
        return parent != null && parent.getRelativeDistanceFromCenter() != null
            && parent.getRelativeDistanceFromCenter().unScaledDistance > 1.4 ? OUTER_VACUUM : MOON;
    }
    String folder() { return "galactic_" + name().toLowerCase(Locale.ROOT); }
    boolean nativeSun() {
        return this == HORUS || this == NEPER || this == MAAHES || this == SETH || this == ANUBIS
            || this == MEHEN || this == ROSS_B || this == ROSS_BA;
    }
}
