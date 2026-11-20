package io.github.andrexifor.banco.mixin;

import io.github.andrexifor.banco.juego.HuellaPack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** El resource pack se ofrece con la huella que calculó el mod (HuellaPack), no con la del server.properties. */
@Mixin(DedicatedServer.class)
public class DedicatedServerMixin {
    @Inject(method = "getServerResourcePack", at = @At("RETURN"), cancellable = true)
    private void banco$huellaPack(CallbackInfoReturnable<Optional<MinecraftServer.ServerResourcePackInfo>> cir) {
        Optional<MinecraftServer.ServerResourcePackInfo> actual = cir.getReturnValue();
        if (actual == null || actual.isEmpty()) return;
        MinecraftServer.ServerResourcePackInfo info = actual.get();
        String huella = HuellaPack.huella(info.url());
        if (huella == null || huella.equalsIgnoreCase(info.hash())) return;
        cir.setReturnValue(Optional.of(new MinecraftServer.ServerResourcePackInfo(
                HuellaPack.id(huella), info.url(), huella, info.isRequired(), info.prompt())));
    }
}
