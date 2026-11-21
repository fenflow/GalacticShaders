package dev.galacticshaders.loader;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

public final class UniformTransformer implements IClassTransformer {
    public byte[] transform(String raw, String name, byte[] bytes) {
        if (bytes == null) return null;
        if ("net.coderbot.iris.Iris".equals(name)) {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String method, String desc, String sig, String[] errors) {
                    MethodVisitor target = super.visitMethod(access, method, desc, sig, errors);
                    if (!method.equals("getCurrentDimensionName") || !desc.equals("()Ljava/lang/String;")) return target;
                    return new MethodVisitor(Opcodes.ASM9, target) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ARETURN) visitMethodInsn(Opcodes.INVOKESTATIC,
                                "dev/galacticshaders/StationLighting", "dimensionName",
                                "(Ljava/lang/String;)Ljava/lang/String;", false);
                            super.visitInsn(opcode);
                        }
                    };
                }
            }, 0);
            return writer.toByteArray();
        }
        if ((name.startsWith("galaxyspace.") && name.contains("SkyProvider"))
            || name.equals("micdoodle8.mods.galacticraft.core.client.SkyProviderOrbit")) return sky(bytes);
        if (!"net.coderbot.iris.uniforms.CommonUniforms".equals(name)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String sig, String[] errors) {
                MethodVisitor target = super.visitMethod(access, method, desc, sig, errors);
                if (!method.equals("addNonDynamicUniforms") || !desc.startsWith("(Lnet/coderbot/iris/gl/uniform/UniformHolder;")) return target;
                return new MethodVisitor(Opcodes.ASM9, target) {
                    @Override public void visitCode() {
                        super.visitCode();
                        visitVarInsn(Opcodes.ALOAD, 0);
                        visitMethodInsn(Opcodes.INVOKESTATIC, "dev/galacticshaders/PlanetUniform", "register",
                            "(Lnet/coderbot/iris/gl/uniform/UniformHolder;)V", false);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] sky(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String desc, String sig, String[] errors) {
                MethodVisitor target = super.visitMethod(access, method, desc, sig, errors);
                if (reader.getClassName().equals("micdoodle8/mods/galacticraft/core/client/SkyProviderOrbit")) {
                    return new MethodVisitor(Opcodes.ASM9, target) {
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                            super.visitFieldInsn(opcode, owner, field, descriptor);
                            if (opcode == Opcodes.GETSTATIC && field.equals("sunTexture")
                                && descriptor.equals("Lnet/minecraft/util/ResourceLocation;")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/galacticshaders/CelestialTextures",
                                    "sun", "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/util/ResourceLocation;", false);
                            }
                        }
                    };
                }
                // Some providers (notably the Kuiper Belt) draw the solar
                // texture through the same helper as orbiting bodies. Mark
                // only that quad, before the camera transforms its vertices.
                if (method.equals("drawTexture") && desc.equals("(Lnet/minecraft/util/ResourceLocation;D)V"))
                    return new MethodVisitor(Opcodes.ASM9,target) {
                        @Override public void visitLdcInsn(Object value) {
                            if (Double.valueOf(-100.0).equals(value)) {
                                super.visitVarInsn(Opcodes.ALOAD,1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/galacticshaders/CelestialTextures",
                                    "texturedQuadY","(Lnet/minecraft/util/ResourceLocation;)D",false);
                            } else super.visitLdcInsn(value);
                        }
                    };
                if (!method.equals("getSunTexture") || !desc.equals("()Lnet/minecraft/util/ResourceLocation;")) return target;
                return new MethodVisitor(Opcodes.ASM9,target) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) visitMethodInsn(Opcodes.INVOKESTATIC,
                            "dev/galacticshaders/CelestialTextures", "sun",
                            "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/util/ResourceLocation;", false);
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }
}
