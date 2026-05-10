package grill24.workingwolves;

import grill24.workingwolves.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTabs {
    public static Holder<CreativeModeTab> WORKING_WOLVES_TAB;

    public static void registerCreativeTabs() {
        var api = RegistrationApiSided.getInstance();

        WORKING_WOLVES_TAB = api.registerCreativeModeTab("working_wolves_tab", loc ->
                CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 2)
                        .title(Component.translatable("itemGroup.workingwolves"))
                        .icon(() -> new ItemStack(ModItems.GOLD_TRIMMED_COLLAR.value()))
                        .displayItems((params, output) -> {
                            output.accept(ModItems.LEATHER_COLLAR.value());
                            output.accept(ModItems.IRON_STUDDED_COLLAR.value());
                            output.accept(ModItems.GOLD_TRIMMED_COLLAR.value());
                            output.accept(ModItems.DISPATCH_WHISTLE.value());
                            output.accept(ModItems.RECALL_WHISTLE.value());
                            output.accept(ModBlocks.DOG_BED.value());
                        })
                        .build());
    }
}
