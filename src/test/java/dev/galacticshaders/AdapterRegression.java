package dev.galacticshaders;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import micdoodle8.mods.galacticraft.api.galaxies.*;

/** Run against user-supplied original packs. No shader-pack source is redistributed. */
public final class AdapterRegression {
    public static void main(String[] args) throws Exception {
        Properties localized = new Properties();
        String label = "\u03b1 Centauri Bb";
        localized.load(new ByteArrayInputStream(("body=" + PackAdapter.propertyName(label)).getBytes("ISO-8859-1")));
        require(label.equals(localized.getProperty("body")),"Localized name did not survive Iris properties decoding");
        check(new Planet("CentauriBb"), Environment.CENTAURI_BB);
        check(new Planet("Barnarda2"), Environment.BARNARDA_C);
        check(new Planet("Barnarda4"), Environment.BARNARDA_E);
        check(new Planet("Barnarda5"), Environment.BARNARDA_F);
        check(new Planet("Vega1"), Environment.VEGA_B);
        check(new Planet("TCetiE"), Environment.TAU_CETI_E);
        check(new Planet("asteroidBeltMehen"), Environment.MEHEN);
        check(new Planet("Ross128b"), Environment.ROSS_B);
        check(new Moon("Ross128ba").setParentPlanet(new Planet("Ross128b")), Environment.ROSS_BA);
        check(new Moon("Europa").setParentPlanet(new Planet("jupiter")), Environment.OUTER_VACUUM);
        check(new Moon("Triton").setParentPlanet(new Planet("neptune")), Environment.NEPTUNIAN_MOON);
        check(new Planet("Ceres"), Environment.ASTEROIDS);
        check(new Planet("Makemake"), Environment.KUIPER);
        Map<Environment,Set<String>> names = new EnumMap<>(Environment.class);
        for (Environment e : Environment.values()) names.put(e,Collections.singleton("QA " + e.name()));
        Path temp=Files.createTempDirectory("galacticshaders-test-");
        for (String archive : new String[]{"BSL_v10.1.8.zip","ComplementaryReimagined_r5.9.3.zip","photon_v1.4.zip"}) {
            Path original=Paths.get(args[0],archive);
            Path copy=temp.resolve(archive);
            Files.copy(original,copy);
            PackAdapter.generate(temp,archive,names);
            Path output=temp.resolve("GalacticShaders-0.3-"+archive);
            try(ZipFile source=new ZipFile(copy.toFile()); ZipFile result=new ZipFile(output.toFile())) {
                for(ZipEntry entry:Collections.list(source.entries())) {
                    if(entry.isDirectory() || !entry.getName().startsWith("shaders/world0/")) continue;
                    ZipEntry preserved=result.getEntry(entry.getName());
                    require(preserved!=null && preserved.getCrc()==entry.getCrc(),"Vanilla program changed: "+entry.getName());
                    String leaf=entry.getName().substring("shaders/world0/".length());
                    for(Environment e:Environment.values()) require(result.getEntry("shaders/"+e.folder()+"/"+leaf)!=null,"Missing "+e+"/"+leaf);
                }
                if (!archive.startsWith("photon")) {
                    String deferred=read(result,"shaders/program/deferred1.glsl");
                    int disk=deferred.indexOf("float gsCoverage");
                    if (archive.startsWith("BSL")) require(disk>deferred.indexOf("else if (vxZ < 1.0)"),"Solar disk outside sky branch");
                    else require(disk<0,"Complementary must use its own solar renderer");
                    require(read(result,"shaders/galactic_moon/deferred1.fsh").contains("GS_NATIVE_SUN 0"),"Missing lunar solar disk");
                    require(read(result,"shaders/galactic_horus/deferred1.fsh").contains("GS_NATIVE_SUN 1"),"Duplicate Amun-Ra sun");
                }
                if (archive.startsWith("photon")) require(read(result,"shaders/program/d2_clouds_upscaling.fsh").contains("gsDepthFetch(lod_depth_tex_shading, dst_texel"),"Unbounded Roxy fallback depth fetch");
                String pluto=read(result,"shaders/galactic_kuiper/gbuffers_terrain.fsh");
                require(pluto.contains("GS_SUN 6.0E-4") && pluto.contains("GS_DAY_STARS 0.62"),"Pluto lost physical solar flux or daytime stars");
                String terrain=pluto.split("GS_TERRAIN_SUN ")[1].split("\\s+")[0];
                require(Double.parseDouble(terrain)>0.10 && Double.parseDouble(terrain)<0.20,"Pluto daylight outside chosen range");
                require(read(result,"shaders/galactic_titan/gbuffers_terrain.fsh").contains("GS_DAY_STARS 0.0"),"Stars leaked through Titan surface clouds");
                if(archive.startsWith("Complementary")) {
                    String sky=read(result,"shaders/program/gbuffers_skytextured.glsl");
                    require(sky.indexOf("isSun=GS_NATIVE_SUN")>sky.indexOf("if (sunSideCheck) isSun = true"),"Wrong final solar classification");
                    require(sky.contains("gl_FragData[0]=body;return;"),"Body entered solar HDR path");
                }
                String props=read(result,"shaders/shaders.properties");
                if(archive.startsWith("BSL")) require(props.contains("program.galactic_kuiper/composite7.enabled=false"),"Vacuum temporal pass enabled");
                String mapping=read(result,"shaders/dimension.properties");
                require(mapping.contains("dimension.galactic_earth=Overworld") && !mapping.contains("minecraft:overworld"),"Conflicting Overworld mapping");
                require(result.getEntry("shaders/galactic_earth/gbuffers_terrain.fsh") != null,"Missing Earth program");
                for(Environment e:Environment.values()) require(mapping.contains("dimension."+e.folder()+"=\"QA "+e.name()+"\""),"Missing quoted mapping "+e);
            }
            long modified=Files.getLastModifiedTime(output).toMillis();
            PackAdapter.generate(temp,archive,names);
            require(Files.getLastModifiedTime(output).toMillis()==modified,"Unchanged pack rewritten");
            require(Arrays.equals(Files.readAllBytes(original),Files.readAllBytes(copy)),"Original modified");
            System.out.println("PASS " + archive + " — " + names.size() + " profiles");
        }
        System.out.println("PASS registry-name regressions; output " + temp);
    }
    private static void check(CelestialBody b,Environment e) { require(Environment.of(b)==e,b.getName()+" mapped to "+Environment.of(b)); }
    private static void require(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private static String read(ZipFile zip,String path)throws IOException {
        try(InputStream in=zip.getInputStream(zip.getEntry(path)); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n; while((n=in.read(b))>=0)out.write(b,0,n); return out.toString("UTF-8");
        }
    }
}
