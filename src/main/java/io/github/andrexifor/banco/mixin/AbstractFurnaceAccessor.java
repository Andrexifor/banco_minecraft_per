package io.github.andrexifor.banco.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** La Huayrachina enciende su fuego con lava sin poner un balde en el horno, y el Soplo del Wayra lo apura. */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceAccessor {
    @Accessor("litTimeRemaining")
    int getLitTimeRemaining();

    @Accessor("litTimeRemaining")
    void setLitTimeRemaining(int valor);

    @Accessor("litTotalTime")
    int getLitTotalTime();

    @Accessor("litTotalTime")
    void setLitTotalTime(int valor);

    @Accessor("cookingTimer")
    int getCookingTimer();

    @Accessor("cookingTimer")
    void setCookingTimer(int valor);

    @Accessor("cookingTotalTime")
    int getCookingTotalTime();
}
