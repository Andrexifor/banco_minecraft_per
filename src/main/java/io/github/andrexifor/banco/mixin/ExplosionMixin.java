package io.github.andrexifor.banco.mixin;

import io.github.andrexifor.banco.juego.Proteccion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Las explosiones (creepers, TNT, ghasts) no rompen bloques dentro de las zonas protegidas.
 * El daño a entidades no cambia. Si el método no existe en esta versión, Mixin lo omite
 * (defaultRequire 0) y el servidor arranca igual.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void bancoMinecraftPer$protegerZonas(CallbackInfoReturnable<List<BlockPos>> cir) {
        Explosion self = (Explosion) (Object) this;
        List<BlockPos> posiciones = cir.getReturnValue();
        if (posiciones == null || posiciones.isEmpty()) return;
        List<BlockPos> filtradas = new ArrayList<>(posiciones.size());
        for (BlockPos pos : posiciones) {
            if (!Proteccion.protegido(self.level(), pos)) filtradas.add(pos);
        }
        if (filtradas.size() != posiciones.size()) {
            Proteccion.explosionFrenada(posiciones.size() - filtradas.size());
            cir.setReturnValue(filtradas);
        }
    }
}
