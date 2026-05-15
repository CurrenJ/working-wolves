package grill24.workingwolves.mixin;

import grill24.workingwolves.Config;
import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void workingwolves$preventSwellAgainstWolves(CallbackInfo ci) {
        if (!Config.hunterCreeperSafe) return;
        Creeper self = (Creeper) (Object) this;
        LivingEntity target = self.getTarget();
        if (target == null) target = self.getLastHurtByMob();
        if (target instanceof Wolf wolf && wolf.isTame()) {
            IWorkingWolf ww = (IWorkingWolf) (Object) wolf;
            if (ww.workingwolves$getCollarTier() > 0) {
                self.setSwellDir(-1);
                self.setTarget(null);
            }
        }
    }
}
