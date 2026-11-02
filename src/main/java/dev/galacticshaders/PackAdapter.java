package dev.galacticshaders;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class PackAdapter {
    static void generate(Path packs, String archive, Map<Environment,Set<String>> names) throws IOException {
        Path source = packs.resolve(archive);
        if (!Files.isRegularFile(source)) return;
        Path tmp = Files.createTempFile(packs, ".galactic-", ".tmp");
        try {
            try (ZipFile original = new ZipFile(source.toFile());
                 ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
                String mapping = "dimension.world0=*\ndimension.world1=minecraft:the_end\ndimension.world-1=minecraft:the_nether\n";
                for (ZipEntry entry : Collections.list(original.entries())) {
                    if (entry.isDirectory()) continue;
                    String path = entry.getName();
                    if (path.equals("shaders/dimension.properties")) {
                        mapping = read(original, entry);
                        continue;
                    }
                    if (path.equals("shaders/shaders.properties")) {
                        StringBuilder properties = new StringBuilder();
                        for (String line : read(original,entry).split("\\r?\\n")) {
                            properties.append(line).append('\n');
                            if (line.trim().startsWith("program.world0/")) {
                                properties.append(line.replace("program.world0/","program.galactic_earth/")).append('\n');
                                for (Environment env : names.keySet()) {
                                    String setting = line.replace("program.world0/","program." + env.folder() + "/");
                                    if (archive.startsWith("BSL") && env.vacuum && line.contains("/composite7.enabled"))
                                        setting = setting.substring(0,setting.indexOf('=')+1) + "false";
                                    properties.append(setting).append('\n');
                                }
                            }
                        }
                        properties.append("uniform.float.gsEyeAltitude=eyeAltitude\n");
                        put(out,path,properties.toString());
                    } else if (path.startsWith("shaders/world0/")) {
                        String program = read(original, entry);
                        put(out, path, program);
                        int include = program.indexOf("#include");
                        String earth = include < 0 ? program : program.substring(0,include) + "#define GS_EARTH\n"
                            + altitude(path) + program.substring(include);
                        put(out,path.replace("shaders/world0/","shaders/galactic_earth/"),earth);
                        for (Environment env : names.keySet()) {
                            String variant = include >= 0
                                ? program.substring(0,include) + env.defines + "#define GS_NATIVE_SUN "
                                    + (env.nativeSun() ? "1\n" : "0\n") + altitude(path) + program.substring(include) : program;
                            put(out, "shaders/" + env.folder() + "/" + path.substring("shaders/world0/".length()), variant);
                        }
                    } else if (path.endsWith(".glsl") || path.endsWith(".fsh")) {
                        put(out, path, NativeLighting.patch(archive, path, read(original, entry)));
                    } else {
                        out.putNextEntry(new ZipEntry(path));
                        try (InputStream in = original.getInputStream(entry)) { copy(in,out); }
                        out.closeEntry();
                    }
                }
                StringBuilder mappings = new StringBuilder(mapping.replace("minecraft:overworld", "")).append("\ndimension.galactic_earth=Overworld\n");
                for (Map.Entry<Environment,Set<String>> group : names.entrySet()) {
                    mappings.append("dimension.").append(group.getKey().folder()).append('=');
                    for (String name : group.getValue()) mappings.append('"').append(propertyName(name)).append("\" ");
                    mappings.append('\n');
                }
                put(out,"shaders/dimension.properties",mappings.toString());
            }
            install(tmp,packs.resolve("GalacticShaders-0.3-" + archive));
        } finally { Files.deleteIfExists(tmp); }
    }
    private static String altitude(String path) {
        if (path.endsWith(".json")) return "";
        // Voxy supplies cameraPosition through its existing uniform buffer.
        String input = path.contains("/voxy_") ? "#define GS_VOXY\n#define GS_HEIGHT cameraPosition.y\n"
            : "#define GS_HEIGHT gsEyeAltitude\n";
        return input + "#define GS_ORBIT smoothstep(255.0,800.0,GS_HEIGHT)\n"
            + "#define GS_AIR (1.0-GS_ORBIT)\n";
    }
    static String propertyName(String name) {
        StringBuilder escaped = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c == '"') continue;
            // Iris reads .properties as ISO-8859-1; preserve localized provider names.
            if (c > 126 || c < 32 || c == '\\') escaped.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
            else escaped.append(c);
        }
        return escaped.toString();
    }
    private static String read(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            copy(in,bytes);
            return bytes.toString("UTF-8");
        }
    }
    private static void put(ZipOutputStream out, String path, String text) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
    static void install(Path tmp, Path target) throws IOException {
        if (Files.exists(target)) {
            try (ZipFile prior = new ZipFile(target.toFile()); ZipFile next = new ZipFile(tmp.toFile())) {
                boolean same = prior.size() == next.size() && next.stream().allMatch(entry -> {
                    ZipEntry old = prior.getEntry(entry.getName());
                    return old != null && old.getCrc() == entry.getCrc() && old.getSize() == entry.getSize();
                });
                if (same) return;
            } catch (ZipException invalid) { /* Replace an interrupted or corrupt previous output. */ }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
    }
}
