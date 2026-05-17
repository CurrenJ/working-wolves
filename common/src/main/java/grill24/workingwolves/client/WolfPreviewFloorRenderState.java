package grill24.workingwolves.client;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record WolfPreviewFloorRenderState(
        EntityRenderState entityRenderState,
        Vector3f entityTranslation,
        Quaternionf entityRotation,
        @Nullable Quaternionf overrideCameraAngle,
        int x0, int y0, int x1, int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds,
        List<BlockState> floorBlocks,
        float scrollOffsetZ, float scrollOffsetX,
        int floorOriginX, int floorOriginZ
) implements PictureInPictureRenderState {

    public WolfPreviewFloorRenderState(
            EntityRenderState entityRenderState,
            Vector3f entityTranslation,
            Quaternionf entityRotation,
            @Nullable Quaternionf overrideCameraAngle,
            int x0, int y0, int x1, int y1,
            float scale,
            @Nullable ScreenRectangle scissorArea,
            List<BlockState> floorBlocks,
            float scrollOffsetZ, float scrollOffsetX,
            int floorOriginX, int floorOriginZ
    ) {
        this(entityRenderState, entityTranslation, entityRotation, overrideCameraAngle,
                x0, y0, x1, y1, scale, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea),
                floorBlocks, scrollOffsetZ, scrollOffsetX,
                floorOriginX, floorOriginZ);
    }
}
