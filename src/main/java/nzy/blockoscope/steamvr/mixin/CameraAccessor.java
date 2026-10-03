package nzy.blockoscope.steamvr.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Moves the camera object itself to each eye while that eye renders (see StereoRenderer.render). */
@Mixin(value = Camera.class, remap = false)
public interface CameraAccessor {
    @Invoker("setPosition")
    void blockoscopeSteamVr$setPosition(Vec3 position);
}
