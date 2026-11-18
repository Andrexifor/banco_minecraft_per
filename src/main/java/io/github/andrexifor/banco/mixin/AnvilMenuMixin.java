package io.github.andrexifor.banco.mixin;

import io.github.andrexifor.banco.juego.Encantamientos;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Regla del servidor: el yunque no puede poner Reparación (Mending) en un ítem legendario
 * o con encantamientos por encima del máximo vanilla. Si el resultado la tendría,
 * la casilla de resultado se vacía. Solo corre en el servidor: los clientes no necesitan nada.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void bancoMinecraftPer$bloquearReparacion(CallbackInfo ci) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        // Casillas del yunque: 0 = izquierda, 1 = derecha, 2 = resultado
        ItemStack resultado = self.getSlot(2).getItem();
        if (Encantamientos.bloquearResultadoYunque(resultado)) {
            self.getSlot(2).set(ItemStack.EMPTY);
        }
    }
}
