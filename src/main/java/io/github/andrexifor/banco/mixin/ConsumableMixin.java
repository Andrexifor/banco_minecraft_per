package io.github.andrexifor.banco.mixin;

import io.github.andrexifor.banco.juego.Pociones;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pociones del Kallawaya: al terminar de tomarlas, el mod aplica su efecto (o cancela si no se pudo). */
@Mixin(Consumable.class)
public class ConsumableMixin {
    @Inject(method = "onConsume", at = @At("HEAD"), cancellable = true)
    private void banco$pociones(Level level, LivingEntity entity, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (entity instanceof ServerPlayer jugador && !Pociones.alBeber(jugador, stack)) {
            cir.setReturnValue(stack);
        }
    }
}
