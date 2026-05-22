package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.blockentity.expedition.data.RoleEntry;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;

class ToolDurabilityHelper {

    private static final int TOOL_LOW_THRESHOLD = 5;

    static boolean apply(Level level, int baseDamage, String toolTagId, RoleEntry.ToolMessages messages, ExpeditionSimulator sim) {
        String[] parts = toolTagId.split(":", 2);
        String ns = parts.length == 2 ? parts[0] : "minecraft";
        String path = parts.length == 2 ? parts[1] : parts[0];
        TagKey<Item> toolTag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ns, path));
        return applyToolDurability(level, baseDamage, toolTag, messages, sim);
    }

    private static String pick(java.util.List<String> lines, Level level) {
        if (lines.isEmpty()) return "";
        return lines.get(level.getRandom().nextInt(lines.size()));
    }

    private static boolean applyToolDurability(Level level, int baseDamage, TagKey<Item> toolTag,
            RoleEntry.ToolMessages messages, ExpeditionSimulator sim) {

        if (!(level instanceof ServerLevel sl)) return true;
        Entity entity = sl.getEntity(sim.getWolfUuid());
        if (!(entity instanceof Wolf wolf)) return true;
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        BlockState testBlock = messages.updateMiningStats()
            ? Blocks.STONE.defaultBlockState()
            : Blocks.OAK_LOG.defaultBlockState();

        int bestSlot = findBestToolSlot(bag, toolTag, testBlock, -1);

        if (bestSlot < 0) {
            sim.addLogLine(pick(messages.noTool(), level));
            return false;
        }

        ItemStack tool = bag.get(bestSlot);
        int unbreaking = 0;
        boolean isEnchanted = tool.isEnchanted();
        for (var entry : tool.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.UNBREAKING)) {
                unbreaking = entry.getIntValue();
            }
        }

        int currentDamage = tool.getDamageValue();
        int maxDurability = tool.getMaxDamage();
        int remainingBefore = maxDurability - currentDamage;

        if (isEnchanted && remainingBefore <= 1) {
            if (hasSpare(bag, bestSlot, toolTag)) {
                switchToNextTool(bag, bestSlot, toolTag, testBlock, messages.updateMiningStats(), sim);
                sim.addLogLine(pick(messages.switchedSpare(), level));
                return true;
            }
            sim.addLogLine(pick(messages.preciousBreak(), level));
            return false;
        }

        int actualDamage = 0;
        for (int d = 0; d < baseDamage; d++) {
            if (unbreaking == 0 || level.getRandom().nextInt(unbreaking + 1) == 0) {
                actualDamage++;
            }
        }
        if (actualDamage == 0) return true;

        int newDamage = currentDamage + actualDamage;

        if (isEnchanted && newDamage >= maxDurability) {
            tool.setDamageValue(maxDurability - 1);
            if (hasSpare(bag, bestSlot, toolTag)) {
                switchToNextTool(bag, bestSlot, toolTag, testBlock, messages.updateMiningStats(), sim);
                sim.addLogLine(pick(messages.switchedSpare(), level));
                return true;
            }
            sim.addLogLine(pick(messages.preciousBreak(), level));
            return false;
        }

        if (newDamage >= maxDurability) {
            bag.set(bestSlot, ItemStack.EMPTY);
            sim.addLogLine(pick(messages.broken(), level));
            if (!switchToNextTool(bag, -1, toolTag, testBlock, messages.updateMiningStats(), sim)) {
                sim.addLogLine(pick(messages.lastGone(), level));
                return false;
            }
            return true;
        }

        tool.setDamageValue(newDamage);

        int remainingAfter = maxDurability - newDamage;
        if (remainingAfter < TOOL_LOW_THRESHOLD && !hasSpare(bag, bestSlot, toolTag)) {
            sim.addLogLine(pick(messages.nearlyDone(), level));
            return false;
        }

        return true;
    }

    static boolean hasSpare(NonNullList<ItemStack> bag, int excludeSlot, TagKey<Item> toolTag) {
        for (int i = 0; i < bag.size(); i++) {
            if (i == excludeSlot) continue;
            if (!bag.get(i).isEmpty() && bag.get(i).is(toolTag)) return true;
        }
        return false;
    }

    private static boolean switchToNextTool(NonNullList<ItemStack> bag, int excludeSlot,
            TagKey<Item> toolTag, BlockState testBlock, boolean updateMiningStats, ExpeditionSimulator sim) {
        int bestSlot = findBestToolSlot(bag, toolTag, testBlock, excludeSlot);
        if (bestSlot < 0) return false;

        if (updateMiningStats) {
            ItemStack tool = bag.get(bestSlot);
            sim.setPickaxeSpeed(tool.getDestroySpeed(testBlock));
            sim.setFortune(0);
            sim.setSilkTouch(false);
            for (var entry : tool.getEnchantments().entrySet()) {
                if (entry.getKey().is(Enchantments.FORTUNE)) sim.setFortune(Math.max(sim.getFortune(), entry.getIntValue()));
                if (entry.getKey().is(Enchantments.SILK_TOUCH) && entry.getIntValue() > 0) sim.setSilkTouch(true);
            }
        } else {
            sim.setAxeSpeed(bag.get(bestSlot).getDestroySpeed(testBlock));
        }
        return true;
    }

    private static int findBestToolSlot(NonNullList<ItemStack> bag, TagKey<Item> toolTag,
            BlockState testBlock, int excludeSlot) {
        int bestSlot = -1;
        float bestSpeed = 0;
        for (int i = 0; i < bag.size(); i++) {
            if (i == excludeSlot) continue;
            ItemStack stack = bag.get(i);
            if (!stack.isEmpty() && stack.is(toolTag)) {
                float speed = stack.getDestroySpeed(testBlock);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }
}
