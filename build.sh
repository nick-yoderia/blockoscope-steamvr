#!/usr/bin/env bash
# Builds build/stereo-theater-<version>.jar with the JDK that ships with Prism Launcher.
# Compiles against Mixin, LWJGL, JOML, the Minecraft client jar, Fabric Loader, Cloth Config, Mod Menu and
# Iris (none are bundled; Iris support only loads when Iris is installed).
set -euo pipefail
cd "$(dirname "$0")"

MC_VERSION="26.2"   # must match the Prism instance; Prism's library folder can hold other versions too
JDK="$APPDATA/PrismLauncher/java/java-runtime-epsilon/bin"
LIBS="$APPDATA/PrismLauncher/libraries"
MIXIN="$(ls "$LIBS"/net/fabricmc/sponge-mixin/*/sponge-mixin-*.jar | tail -1)"
LWJGL="$(ls "$LIBS"/org/lwjgl/lwjgl/*/lwjgl-*.jar | grep -v natives | tail -1)"
GLFW="$(ls "$LIBS"/org/lwjgl/lwjgl-glfw/*/lwjgl-glfw-*.jar | grep -v natives | tail -1)"
GL="$(ls "$LIBS"/org/lwjgl/lwjgl-opengl/*/lwjgl-opengl-*.jar | grep -v natives | tail -1)"
MC="$LIBS/com/mojang/minecraft/$MC_VERSION/minecraft-$MC_VERSION-client.jar"
JOML="$(ls "$LIBS"/org/joml/joml/*/joml-*.jar | tail -1)"
MODS="$APPDATA/PrismLauncher/instances/26.2-Stereo-Dev/minecraft/mods"
CLOTH="$(ls "$MODS"/cloth-config-*.jar | tail -1)"
MODMENU="$(ls "$MODS"/modmenu-*.jar | tail -1)"
IRIS="$(ls "$APPDATA"/PrismLauncher/instances/26.2/minecraft/mods/iris-fabric-*.jar | tail -1)"
SODIUM="$(ls "$MODS"/sodium-fabric-*.jar | tail -1)"
VOXY="$(ls "$MODS"/voxy-*.jar | tail -1)"
LOADER="$(ls "$LIBS"/net/fabricmc/fabric-loader/*/fabric-loader-*.jar | tail -1)"
ASM_TREE="$(ls "$LIBS"/org/ow2/asm/asm-tree/*/asm-tree-*.jar | tail -1)"
CP=""
MOJANG_LIBS="$(ls "$LIBS"/com/mojang/brigadier/*/brigadier-*.jar "$LIBS"/com/mojang/datafixerupper/*/datafixerupper-*.jar "$LIBS"/org/jspecify/jspecify/*/jspecify-*.jar "$LIBS"/it/unimi/dsi/fastutil/*/fastutil-*.jar 2>/dev/null)"
for jar in "$MIXIN" "$LWJGL" "$GLFW" "$GL" "$JOML" "$MC" "$CLOTH" "$MODMENU" "$IRIS" "$SODIUM" "$VOXY" "$LOADER" "$ASM_TREE" $MOJANG_LIBS; do
  CP="$CP$(cygpath -w "$jar");"
done
VERSION="$(sed -n 's/.*"version": "\(.*\)".*/\1/p' src/main/resources/fabric.mod.json)"

[ -f "$MC" ] || { echo "Minecraft $MC_VERSION client jar not found: $MC" >&2; exit 1; }
rm -rf build && mkdir -p build/classes
"$JDK/javac.exe" --release 17 -Xlint:all,-classfile -cp "$CP" -d build/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/classes/
"$JDK/jar.exe" --create --file "build/stereo-theater-$VERSION.jar" -C build/classes .
echo "Built build/stereo-theater-$VERSION.jar"
