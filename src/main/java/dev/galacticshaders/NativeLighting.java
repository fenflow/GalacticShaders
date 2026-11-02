package dev.galacticshaders;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version-pinned edits at the shader packs' lighting and atmospheric integration points.
 * Profile defines come from the selected dimension's program, including its Voxy programs. */
final class NativeLighting {
    static String patch(String archive, String path, String source) throws IOException {
        source=earth(archive,path,source);
        String result;
        if (archive.startsWith("BSL")) result=bsl(path,source);
        else if (archive.startsWith("Complementary")) result=complementary(path,source);
        else result=photon(path,source);
        if (!result.contains("GS_AIR") && !result.contains("GS_ORBIT")) return result;
        // Declare only where referenced, after the wrapper extension directives.
        return "#if (defined(GS_PLANET) || defined(GS_EARTH)) && !defined(GS_VOXY) && !defined(GS_HEIGHT_DECLARED)\n"
            + "#define GS_HEIGHT_DECLARED\nuniform float gsEyeAltitude;\n#endif\n" + result;
    }

    // These paths are compiled for vanilla dimensions too. Keep their original branches intact.
    // Keep the Overworld pack's own surface atmosphere. Apply its altitude multiplier
    // to native scattering/cloud contributions, before celestial objects are drawn.
    private static String earth(String archive,String path,String s) {
        String anchor=null, code=null;
        if (archive.startsWith("BSL")) {
            if (path.endsWith("/atmospherics/sky.glsl")) { anchor="return sky;"; code="sky *= GS_AIR;"; }
            if (path.endsWith("/atmospherics/fog.glsl")) { anchor="color = mix(color, fogColor, fog);"; code="fog *= GS_AIR;"; }
            if (path.endsWith("/atmospherics/clouds.glsl")) {
                s=earthBefore(s,"return vec4(cloudColor, cloud);","cloud *= GS_AIR;");
                s=earthBefore(s,"return vec4(cloudColor, cloudFaded);","cloudFaded *= GS_AIR;");
                String stars="void DrawStars(inout vec3 color, vec3 viewPos) {";
                s=s.replace(stars,stars+"\n#ifdef GS_EARTH\nif(GS_ORBIT>0.0) {\n"
                    +"vec3 dir=normalize(mat3(gbufferModelViewInverse)*viewPos);\n"
                    +"vec2 uv=420.0*dir.xz/(abs(dir.y)+length(dir.xz)+0.0001);\n"
                    +"color+=vec3(0.8,0.87,1.0)*GS_ORBIT*step(0.994,GetNoise(floor(uv)))"
                    +"*(1.0-smoothstep(0.10,0.48,length(fract(uv)-0.5)))*smoothstep(0.0,0.1,dir.y); }\n#endif\n");
            }
        } else if (archive.startsWith("Complementary")) {
            if (path.endsWith("/atmospherics/sky.glsl")) { anchor="return finalSky;"; code="finalSky *= GS_AIR;"; }
            if (path.endsWith("/clouds/mainClouds.glsl")) { anchor="return clouds;"; code="clouds.a *= GS_AIR;"; }
            if (path.endsWith("/fog/mainFog.glsl")) { anchor="color = mix(color, vec4(fogColorM, 0.0), fog);"; code="fog *= GS_AIR;"; }
        } else {
            if (path.endsWith("/sky/atmosphere.glsl")) {
                s=earthBefore(s,"return atmosphere_post_processing(scattering);","return atmosphere_post_processing(scattering)*GS_AIR;");
                s=earthBefore(s,"return atmosphere_post_processing(atmosphere);","return atmosphere_post_processing(atmosphere)*GS_AIR;");
            }
            if (path.endsWith("/program/c0_vl.fsh")) { anchor="fog_scattering = fog[0];"; code="fog[0]*=GS_AIR; fog[1]=mix(vec3(1.0),fog[1],GS_AIR);"; }
            if (path.endsWith("/sky/stars.glsl")) { anchor="return stable_star_field(coord, star_threshold);"; code="star_threshold=mix(star_threshold,1.0-0.05*STARS_COVERAGE,GS_ORBIT);"; }
        }
        return anchor==null ? s : earthBefore(s,anchor,code);
    }

    private static String earthBefore(String s,String anchor,String code) {
        return s.replace(anchor,"\n#ifdef GS_EARTH\n"+code+"\n#endif\n"+anchor);
    }
    private static String guard(String code) { return "\n#ifdef GS_PLANET\n" + code + "\n#endif\n"; }

    private static String before(String src, String anchor, String code) throws IOException {
        int at = src.indexOf(anchor);
        if (at < 0) throw new IOException("Missing pinned shader hook: " + anchor);
        return src.substring(0,at) + guard(code) + src.substring(at);
    }

    private static String enter(String src, String function, String code) throws IOException {
        Matcher m = Pattern.compile("\\b(?:float|void|vec[234])\\s+" + function + "\\s*\\([^;{}]*\\)\\s*\\{").matcher(src);
        if (!m.find()) throw new IOException("Missing pinned shader function: " + function);
        return src.substring(0,m.end()) + guard(code) + src.substring(m.end());
    }

    private static String sky(String elevation, String daylight) {
        return "return GS_AIR * GS_TINT * 0.32 * (0.68 + 0.32 * (1.0-clamp(" + elevation
            + ",0.0,1.0))) * (0.012 + 0.988 * clamp(" + daylight + ",0.0,1.0));";
    }

    // Shorten the camera exposure for high solar flux before the pack tone maps the HDR scene.
    // Let dark outer-world surfaces use more of the display range in daylight;
    // high-albedo ice and the black of an unlit surface are unaffected.
    private static String darkSurface(String rgb,String daylight) {
        return "if(GS_DAY_STARS>0.0){ float gsPeak=max(max("+rgb+"[0],"+rgb+"[1]),"+rgb+"[2]); "
            +rgb+"*=1.0+1.65*(1.0-smoothstep(0.06,0.40,gsPeak))*clamp("+daylight+",0.0,1.0); } ";
    }
    private static String solarExposure(String variable) {
        return variable + " *= pow(max(GS_SUN,1.0),-0.72);";
    }

    // Custom Galacticraft renderers need not submit vanilla sky triangles. Use the
    // pack's deferred sky branch, after both terrain depth tests and before clouds.
    private static String solarDisk() {
        return "if(GS_NATIVE_SUN==0 && sunVisibility>0.0) {\n"
            + "vec3 gsRay=normalize(viewPos.xyz), gsSun=normalize(sunVec);\n"
            + "float gsRadius=clamp(0.037*sqrt(GS_SUN),0.006,0.105);\n"
            + "float gsDistance=length(gsRay-gsSun)/gsRadius;\n"
            + "float gsEdge=gsDistance;\n"
            + "float gsPixel=max(length(dFdx(gsRay)),length(dFdy(gsRay)))/gsRadius;\n"
            + "float gsCoverage=1.0-smoothstep(1.0-gsPixel,1.0+gsPixel,gsEdge);\n"
            + "float gsCore=1.0-smoothstep(0.74-gsPixel,0.74+gsPixel,gsEdge);\n"
            + "vec3 gsRadiance=vec3(6.0,3.4,0.95)*gsCoverage+vec3(14.0,14.0,12.0)*gsCore"
            + "+vec3(1.15,0.61,0.18)*exp(-1.08*gsDistance)*exp(-0.025*gsDistance*gsDistance);\n"
            + "color.rgb+=gsRadiance*GS_LIGHT_COLOR*sunVisibility*step(0.0,dot(gsRay,gsSun))*(GS_PLANET==2 ? GS_ORBIT : 1.0); }";
    }
    private static String bsl(String path, String s) throws IOException {
        if (path.endsWith("/program/deferred1.glsl")) s=before(s,"#if defined OVERWORLD && defined SKY_DEFERRED",solarDisk());
        if (s.contains("if (moonVisibility > 0.0) DrawStars")) s=s.replace("if (moonVisibility > 0.0) DrawStars",
            "\n#if defined(GS_PLANET) || defined(GS_EARTH)\nif (moonVisibility > 0.0 || GS_ORBIT>0.0\n#ifdef GS_PLANET\n || GS_DAY_STARS>0.0\n#endif\n)\n#else\nif (moonVisibility > 0.0)\n#endif\nDrawStars");
        if (path.endsWith("/program/gbuffers_skybasic.glsl")) {
            s=s.replace("alpha = 1.0;", "alpha = 1.0;" + guard("if(GS_NATIVE_SUN==1 && gl_Color.a<0.999) alpha=0.0; if(abs(gl_Vertex.y-99.9)<0.002 && dot(gl_Color.rgb,gl_Color.rgb)<0.00001) alpha=-1.0;"));
            return enter(s,"main","if(alpha<0.0 || (GS_NATIVE_SUN==1 && alpha<0.999)) discard;");
        }
        if (path.endsWith("/program/gbuffers_skytextured.glsl")) return enter(s,"main",
            "vec4 gsBody=texture2D(texture,texCoord)*color; if(gsBody.a<0.01 || (GS_PLANET==2 && GS_ORBIT==0.0)) discard;\n"
            + "gsBody.rgb=pow(max(gsBody.rgb,vec3(0.0)),vec3(2.2));\n"
            + "gsBody.rgb *= (GS_PLANET==2 ? GS_ORBIT : 1.0);\n"
            + "#if ALPHA_BLEND == 0\ngsBody.rgb=sqrt(gsBody.rgb);\n#endif\n"
            + "gl_FragData[0]=gsBody; return;");
        if (path.endsWith("/atmospherics/sky.glsl")) return enter(s,"GetSkyColor",
            sky("dot(normalize(viewPos),upVec)","sunVisibility"));
        if (path.endsWith("/atmospherics/fog.glsl")) return enter(s,"NormalFog",
            "color = mix(color, GS_TINT * 0.32 * (0.012 + 0.988*sunVisibility), 1.0-exp(-length(viewPos)*GS_EXTINCTION*GS_AIR)); return;");
        if (path.endsWith("/atmospherics/sunmoon.glsl")) {
            s=enter(s,"SunGlare","return;");
            return enter(s,"ShaderSunMoon","return;");
        }
        if (path.equals("shaders/lib/settings.glsl")) return s + guard("#if GS_PLANET == 1\n#undef TAA\n#undef LENS_FLARE\n#endif");
        if (path.endsWith("/program/composite5.glsl")) {
            s=enter(s,"Bloom","if (GS_PLANET == 1) return;");
            return before(s,"Bloom(color, newTexCoord);",solarExposure("color"));
        }
        if (path.endsWith("/atmospherics/clouds.glsl")) {
            s=enter(s,"DrawStars", "if((GS_STARS==0.0 && GS_ORBIT==0.0) || (GS_PLANET==1 && dot(color,color)>0.00001)) return;\n"
                + "vec3 gsDir=normalize(mat3(gbufferModelViewInverse)*viewPos);\n"
                + "vec2 gsUV=gsDir.xz/(abs(gsDir.y)+length(gsDir.xz)+0.0001);\n"
                + "vec2 gsCell=floor(gsUV*420.0);\n"
                + "float gsSeed=GetNoise(gsCell); float gsPoint=1.0-smoothstep(0.10,0.48,length(fract(gsUV*420.0)-0.5));\n"
                + "float gsStars=step(0.994,gsSeed)*gsPoint*smoothstep(0.0,0.1,gsDir.y)*mix(GS_STARS*max(GS_DAY_STARS,pow(1.0-sunVisibility,2.0)),1.0,GS_ORBIT);\n"
                + "color+=vec3(0.8,0.87,1.0)*gsStars*0.85; return;");
            s=enter(s,"CloudCoverageDefault","if (GS_PLANET == 2) return 0.04;");
            String overcast = "if (GS_PLANET == 2) cloudColor = GS_TINT * (0.08 + 0.20*sunVisibility) * (0.65 + 0.35*clamp(dot(cloudColor,vec3(0.33)),0.0,1.0));";
            s=before(s,"return vec4(cloudColor, cloud);",overcast + " cloud *= GS_AIR;");
            s=before(s,"return vec4(cloudColor, cloudFaded);",overcast + " cloudFaded *= GS_AIR;");
            s=enter(s,"DrawCloudSkybox","if (GS_PLANET != 2 && GS_PLANET != 4) return vec4(0.0);");
            s=enter(s,"DrawCloudVolumetric","if (GS_PLANET != 2 && GS_PLANET != 4) { cloudViewLength=0.0; return vec4(0.0); }");
            return enter(s,"DrawAurora","return vec3(0.0);");
        }
        if (path.endsWith("/atmospherics/lightShafts.glsl")) return enter(s,"GetLightShafts","return vec3(0.0);");
        if (path.endsWith("/lighting/forwardLighting.glsl")) {
            s=before(s,"sceneLighting *= skylightSqr",
                "sceneLighting = sunVisibility * (GS_LIGHT_COLOR * GS_TERRAIN_SUN * fullShadow + GS_TINT * GS_FILL * GS_AIR * lightmap.y);");
            s=before(s,"albedo *= max(sceneLighting", darkSurface("albedo","sunVisibility")+"vec3 gsVisionFloor=albedo*nightVision*(0.22+0.23*clamp(vanillaDiffuse,0.0,1.0));");
            s=before(s,"// albedo = blocklightCol", "albedo=max(albedo,gsVisionFloor);");
            return before(s,"#ifdef TOON_LIGHTMAP","minLighting *= GS_FILL*GS_AIR;");
        }
        if (path.endsWith("/lighting/shadows.glsl") && s.contains("GetCloudShadow("))
            return enter(s,"GetCloudShadow","if (GS_PLANET != 4) return 1.0;");
        return s;
    }

    private static String complementary(String path,String s) throws IOException {

        if (path.endsWith("/program/gbuffers_skybasic.glsl")) {
            // Amun-Ra draws translucent untextured stellar auras between its body textures.
            // Treating those triangles as opaque sky erases the companion star behind them.
            // GalaxySpace puts an opaque black backing quad at y=99.9 behind its Sun.
            s=s.replace("glColor = gl_Color;", "glColor = gl_Color;"+guard("if(abs(gl_Vertex.y-99.9)<0.002 && dot(gl_Color.rgb,gl_Color.rgb)<0.00001) glColor.a=-1.0;"));
            s=enter(s,"main","if(glColor.a<0.0 || (GS_NATIVE_SUN==1 && glColor.a<0.999)) discard;");
            return before(s,"#if SUN_MOON_STYLE == 2",
                "if(GS_NATIVE_SUN==1 || (GS_PLANET==2 && GS_ORBIT==0.0)) absVdotS=-1.0;");
        }
        if (path.endsWith("/program/gbuffers_skytextured.glsl")) {
            // GalaxySpace emits its solar quad at local y=+100 and draws
            // planet textures separately (including Earth at y=-100). The
            // legacy Iris sun-side heuristic also matches unrelated bodies.
            s=s.replace("flat in vec4 glColor;", "flat in vec4 glColor;"+guard("flat in float gsSolarQuad;"));
            s=s.replace("flat out vec4 glColor;", "flat out vec4 glColor;"+guard("flat out float gsSolarQuad;"));
            s=s.replace("glColor = gl_Color;", "glColor = gl_Color;"+guard("gsSolarQuad=float(abs(gl_Vertex.y-100.0)<0.01 || abs(gl_Vertex.y+100.03125)<0.005);\n#ifdef GS_ASTEROIDS\nif(abs(gl_Vertex.y-90.0)<0.01) gsSolarQuad=1.0;\n#endif"));
            s=before(s,"if (isSun || isMoon)", "isSun=GS_NATIVE_SUN==0 && gsSolarQuad>0.5; isMoon=false; "
                +"if(!isSun){vec4 body=texture2D(tex,texCoord)*glColor; if(body.a<0.01) discard; body.rgb*=body.rgb;"
                +"body.a*=GS_PLANET==2?GS_ORBIT:1.0; gl_FragData[0]=body;return;}");
            s=before(s,"color.rgb *= 0.25 + 0.75 * sunVisibility2", "color.rgb=vec3(color.r)*vec3(1.0,0.90,0.76);");
            return before(s,"if (isEyeInWater", "color.a*=GS_PLANET==2 ? GS_ORBIT : 1.0;");
        }
        if (path.endsWith("/atmospherics/stars.glsl")) {
            String fade="invRainFactor * pow2(pow2(invNoonFactor2)) * (1.0 - 0.5 * sunVisibility)";
            s="#ifdef GS_PLANET\n#define GS_DAY_STAR_FADE mix(max("+fade+",GS_DAY_STARS),1.0,GS_ORBIT)\n#elif defined(GS_EARTH)\n#define GS_DAY_STAR_FADE mix("+fade+",1.0,GS_ORBIT)\n#else\n#define GS_DAY_STAR_FADE ("+fade+")\n#endif\n"+s.replace(fade,"GS_DAY_STAR_FADE");
            return before(s,"return 40.0 * star", "star *= mix(GS_STARS,1.0,GS_ORBIT)*2.0;");
        }
        if (path.endsWith("/program/composite5.glsl")) {

            return before(s,"DoCompTonemap(color);",solarExposure("color"));
        }
        if (path.endsWith("/fog/bloomFog.glsl")) return enter(s,"GetBloomFog","if (GS_PLANET == 1) return 1.0;");
        if (path.endsWith("/atmospherics/sky.glsl")) return enter(s,"GetSky",sky("VdotU","sunVisibility"));
        if (path.endsWith("/fog/mainFog.glsl")) {
            s=enter(s,"DoBorderFog","return;");
            s=enter(s,"DoCaveFog","return;");
            return enter(s,"DoAtmosphericFog","color.rgb=mix(color.rgb,GS_TINT*0.32*(0.012+0.988*sunVisibility),1.0-exp(-lViewPos*GS_EXTINCTION*GS_AIR)); return;");
        }
        if (path.endsWith("/clouds/mainClouds.glsl")) return before(enter(s,"GetClouds","if (GS_AIR==0.0 || (GS_PLANET != 2 && GS_PLANET != 4)) return vec4(0.0);"), "return clouds;", "clouds.a *= GS_AIR;");
        if (path.endsWith("/volumetricLight/volumetricLight.glsl")) return enter(s,"GetVolumetricLight","return vec4(0.0);");
        if (path.endsWith("/lighting/mainLighting.glsl")) {
            s=before(s,"color.rgb *= finalDiffuse;", darkSurface("color.rgb","sunVisibility")+"finalDiffuse=max(finalDiffuse,vec3(nightVision*(0.24+0.50*pow2(clamp(directionShade*vanillaAO,0.0,1.0)))));");
            return before(s,"float dotSceneLighting",
            "sceneLighting = sunVisibility * (GS_LIGHT_COLOR*sqrt(GS_TERRAIN_SUN)*shadowLightMult + GS_TINT*sqrt(GS_FILL)*GS_AIR*ambientMult); minLighting *= GS_FILL*GS_AIR;");
        }
        return s;
    }

    private static String photon(String path,String s) throws IOException {
        // Roxy binds a 1x1 depth of 1 until LOD targets exist. A full-screen
        // texelFetch on that sampler is undefined outside (0,0); some drivers
        // return zero, turning empty sky into terrain with stale gbuffer data.
        if (!path.contains("/voxy_")) {
            String fixed=s.replaceAll("texelFetch\\((lod_depth_tex(?:_shading|_solid)?|depth_sampler),","gsDepthFetch($1,");
            if (!fixed.equals(s)) {
                s="#ifdef GS_PLANET\nvec4 gsDepthFetch(sampler2D t,ivec2 p,int level){return texelFetch(t,clamp(p,ivec2(0),textureSize(t,level)-ivec2(1)),level);}\n#else\n#define gsDepthFetch texelFetch\n#endif\n"+fixed;
            }
        }
        // The cloud upscaler produces combined depth; use current terrain depths
        // as the authority on empty pixels before sampling the uncleared gbuffers.
        if (path.endsWith("/program/d4_deferred_shading.fsh")) {
            return before(s,"bool is_lod = is_lod_terrain(depth_mc, depth_lod);",
                "if(depth_mc>=1.0 && depth_lod>=1.0) depth=1.0;");
        }
        if (path.endsWith("/program/c4_taa_exposure.fsh")) {
            return before(s,"vec3 closest_view = screen_to_view_space",
                "if(closest.z>=1.0) { vec3 gsNow=texture(colortex0,clamp(uv*taau_render_scale,0.5*view_pixel_size,taau_render_scale-0.5*view_pixel_size)).rgb; result=vec4(gsNow,texel==ivec2(0)?exposure:1.0); bloom_input=gsNow; return; }");
        }
        if (path.endsWith("/sky/sky.glsl")) {
            s=enter(s,"draw_moon","return vec4(0.0);");
            return enter(s,"draw_sun","if(GS_NATIVE_SUN==1) return vec3(0.0); float gsR=clamp(0.011*sqrt(GS_SUN),0.003,0.045); float gsD=length(ray_dir-sun_dir)/gsR; return GS_LIGHT_COLOR * (4.0*(1.0-smoothstep(0.72,1.22,gsD))+0.15*exp(-1.6*gsD))*(GS_PLANET==2?GS_ORBIT:1.0);");
        }
        if (path.endsWith("/sky/rainbow.glsl")) {
            String anchor="return fragment_color + rainbow_color * rainbow_fade;";
            return s.replace(anchor,"\n#ifdef GS_EARTH\nrainbow_fade *= GS_AIR;\n#elif defined(GS_PLANET)\nrainbow_fade *= GS_PLANET==1 ? 0.0 : GS_AIR;\n#endif\n"+anchor);
        }
        if (path.endsWith("/sky/clouds.glsl")) return s.replace("return result;","\n#if defined(GS_PLANET) || defined(GS_EARTH)\nresult.scattering *= GS_AIR; result.transmittance=mix(1.0,result.transmittance,GS_AIR);\n#endif\nreturn result;");
        if (path.endsWith("/sky/stars.glsl")) return before(s,"return stable_star_field(coord, star_threshold);",
            "return stable_star_field(coord,mix(star_threshold,1.0-0.05*STARS_COVERAGE,max(GS_ORBIT,GS_DAY_STARS)))*mix(GS_STARS,1.0,GS_ORBIT)*1.7;");
        if (path.endsWith("/program/gbuffers_skytextured.fsh")) return enter(s,"main",
            "vec4 gsBody=texture(gtexture,uv); if(gsBody.a<0.01 || (GS_PLANET==2 && GS_ORBIT==0.0)) discard;\n"
            + "frag_color=pow(max(gsBody.rgb*tint,vec3(0.0)),vec3(2.2))*rec709_to_working_color;\n"
            + "frag_color *= (GS_PLANET==2 ? GS_ORBIT : 1.0); return;");
        if (path.endsWith("/sky/atmosphere.glsl")) {
            s=before(s,"// Calculate nu, mu, mu_s",sky("ray_dir.y","sun_dir.y*3.0"));
            s=before(s,"vec3 uv = atmosphere_scattering_uv(nu, mu, mu_s);",sky("mu","mu_s*3.0"));
            return s;
        }
        if (path.endsWith("/fog/simple_fog.glsl")) return enter(s,"border_fog","return 1.0;");
        if (path.endsWith("/program/c19_color_grading.fsh")) return before(s,"scene_color = mix(scene_color, bloom, bloom_intensity);","if(GS_PLANET==1) bloom_intensity=0.0;");
        if (path.endsWith("/program/c0_vl.fsh")) return before(s,"fog_scattering = fog[0];",
            "float gsT = depth0==1.0 && (GS_PLANET==3 || GS_PLANET==4) ? 1.0 : exp(-length(world_end_pos-world_start_pos)*GS_EXTINCTION*GS_AIR); fog=mat2x3(GS_TINT*(1.0-gsT)*clamp(sun_dir.y*3.0,0.012,1.0),vec3(gsT));");
        if (path.endsWith("/lighting/diffuse_lighting.glsl")) {
            s=before(s,"return max0(lighting) * material.albedo", darkSurface("material.albedo","sun_dir.y*3.0")+"lighting=max(lighting,vec3(2.0*nightVision));");
            s=enter(s,"get_sky_lighting","return GS_TINT * GS_FILL * GS_AIR * ao * light_levels.y * clamp(sun_dir.y*3.0,0.0,1.0);");
            return before(s,"lighting *= light_color;",
                "lighting = GS_LIGHT_COLOR * GS_TERRAIN_SUN * max(NoL,0.0) * shadows * clamp(sun_dir.y*3.0,0.0,1.0);");
        }
        if (path.equals("shaders/settings.glsl")) return s + guard("#if GS_PLANET != 2 && GS_PLANET != 4\n"
            + "#undef CLOUDS_CUMULUS\n#undef CLOUDS_CUMULUS_CONGESTUS\n#undef CLOUDS_ALTOCUMULUS\n#undef CLOUDS_CIRRUS\n#undef CLOUDS_NOCTILUCENT\n#endif\n"
            + " #undef BLOOMY_FOG\n#undef CREPUSCULAR_RAYS\n#undef CLOUD_SHADOWS\n#undef CAVE_FOG\n#undef BORDER_FOG");
        return s;
    }
}
