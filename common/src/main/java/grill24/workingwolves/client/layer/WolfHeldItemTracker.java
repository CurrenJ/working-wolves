package grill24.workingwolves.client.layer;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Thread-safe frame holder for the current wolf's mouth item. */
public class WolfHeldItemTracker {
    public static final ItemStackRenderState heldItem = new ItemStackRenderState();
}
