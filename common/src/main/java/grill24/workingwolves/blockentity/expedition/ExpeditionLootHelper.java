package grill24.workingwolves.blockentity.expedition;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

class ExpeditionLootHelper {

    static List<ItemStack> roll(ServerLevel sl, ResourceKey<LootTable> key, BlockPos pos) {
        LootTable table = sl.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(sl)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }
}
