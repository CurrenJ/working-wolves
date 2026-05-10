package grill24.workingwolves.neoforge;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.network.WolfDataSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = WorkingWolves.MODID, dist = Dist.CLIENT)
public class WorkingWolvesNeoForgeClient {
    public WorkingWolvesNeoForgeClient(ModContainer container, IEventBus modEventBus) {
        NeoForgePacketRegistrar.wolfDataSyncHandler = this::applyClientData;
    }

    private void applyClientData(WolfDataSyncPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(packet.wolfId());
        if (entity instanceof Wolf wolf) {
            IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
            mixin.workingwolves$setCollarTier(packet.collarTier());
            mixin.workingwolves$setWolfClass(packet.wolfClass().isEmpty() ? null : packet.wolfClass());
            mixin.workingwolves$setBedPos(packet.bedPos().equals(BlockPos.ZERO) ? null : packet.bedPos());
            mixin.workingwolves$setExpeditionState(packet.expeditionState());
            mixin.workingwolves$setExpeditionStartTime(packet.expeditionStartTime());
            mixin.workingwolves$setExpeditionDuration(packet.expeditionDuration());
            mixin.workingwolves$setFilterItem(packet.filterItem());

            if (packet.collarTier() > 0) {
                DyeColor color = switch (packet.collarTier()) {
                    case 1 -> DyeColor.BROWN;
                    case 2 -> DyeColor.GRAY;
                    case 3 -> DyeColor.YELLOW;
                    default -> DyeColor.RED;
                };
                mixin.workingwolves$setCollarColorFromTier(color);
            }
        }
    }
}
