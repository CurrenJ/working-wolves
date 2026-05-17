package grill24.workingwolves.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import grill24.workingwolves.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class WolfPreviewFloorRenderer extends PictureInPictureRenderer<WolfPreviewFloorRenderState> {

    // Floor grid: COLS wide in X, ROWS deep in Z
    static final int FLOOR_COLS = 9;
    static final int FLOOR_ROWS = 6;

    private final EntityRenderDispatcher entityRenderDispatcher;

    public WolfPreviewFloorRenderer(MultiBufferSource.BufferSource bufferSource,
                                    EntityRenderDispatcher entityRenderDispatcher) {
        super(bufferSource);
        this.entityRenderDispatcher = entityRenderDispatcher;
    }

    @Override
    public Class<WolfPreviewFloorRenderState> getRenderStateClass() {
        return WolfPreviewFloorRenderState.class;
    }

    @Override
    protected void renderToTexture(WolfPreviewFloorRenderState state, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);

        // Apply entity-space transforms (same as GuiEntityRenderer)
        poseStack.translate(state.entityTranslation().x(), state.entityTranslation().y(), state.entityTranslation().z());
        poseStack.mulPose(state.entityRotation());

        // Render scrolling floor blocks first (entity renders on top via depth test)
        renderFloor(state.floorBlocks(), state.sideObjects(), state.scrollOffsetZ(), state.scrollOffsetX(),
                state.floorOriginX(), state.floorOriginZ(), poseStack, minecraft);

        // Render wolf entity
        Quaternionf overriddenCameraAngle = state.overrideCameraAngle();
        FeatureRenderDispatcher featureRenderDispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState cameraRenderState = new CameraRenderState();
        if (overriddenCameraAngle != null) {
            cameraRenderState.orientation = overriddenCameraAngle.conjugate(new Quaternionf()).rotateY((float) Math.PI);
        }
        entityRenderDispatcher.submit(
                state.entityRenderState(), cameraRenderState,
                0.0, 0.0, 0.0,
                poseStack, featureRenderDispatcher.getSubmitNodeStorage());
        featureRenderDispatcher.renderAllFeatures();
    }

    private void renderFloor(List<BlockState> blocks, List<BlockState> sideObjects,
                             float scrollOffsetZ, float scrollOffsetX,
                             int floorOriginX, int floorOriginZ,
                             PoseStack poseStack, Minecraft minecraft) {
        if (blocks.isEmpty()) return;

        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(false, false, minecraft.getBlockColors());
        BlockAndTintGetter fakeLevel = FULL_BRIGHT_LEVEL;

        // Scroll wraps within one block to avoid discrete jumps; each axis scrolls independently
        float scrollZ = scrollOffsetZ % 1.0f;
        float scrollX = scrollOffsetX % 1.0f;

        BlockQuadOutput output = (x, y, z, quad, instance) -> {
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            var buffer = bufferSource.getBuffer(switch (quad.materialInfo().layer()) {
                case SOLID -> RenderTypes.solidMovingBlock();
                case CUTOUT -> RenderTypes.cutoutMovingBlock();
                case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
            });
            buffer.putBakedQuad(poseStack.last(), quad, instance);
            poseStack.popPose();
        };

        // Floor-group transform: rotation and scale are shared so blocks stay aligned.
        // Rotation is applied around the grid center, so the floor spins as a unit.
        poseStack.pushPose();
        poseStack.translate(Config.previewFloorOffsetX, Config.previewFloorY, Config.previewFloorOffsetZ);
        poseStack.scale(Config.previewFloorScale, Config.previewFloorScale, Config.previewFloorScale);
        if (Config.previewFloorRotX != 0f)
            poseStack.mulPose(new Quaternionf().rotateX(Config.previewFloorRotX * (float) (Math.PI / 180.0)));
        if (Config.previewFloorRotY != 0f)
            poseStack.mulPose(new Quaternionf().rotateY(Config.previewFloorRotY * (float) (Math.PI / 180.0)));
        if (Config.previewFloorRotZ != 0f)
            poseStack.mulPose(new Quaternionf().rotateZ(Config.previewFloorRotZ * (float) (Math.PI / 180.0)));

        float spacing = Config.previewFloorSpacing;

        // Row-major iteration matches getFloorGridAsList() flat layout
        int blockIndex = 0;
        for (int row = 0; row < FLOOR_ROWS; row++) {
            for (int col = 0; col < FLOOR_COLS; col++) {
                BlockState blockState = blocks.get(blockIndex % blocks.size());
                blockIndex++;
                if (blockState.isAir()) continue;

                float bx = (col - FLOOR_COLS / 2.0f) * spacing + scrollX;
                float bz = (row - FLOOR_ROWS / 2.0f) * spacing + scrollZ;

                BlockStateModel model = modelSet.get(blockState);
                BlockPos blockPos = new BlockPos(col + floorOriginX, 0, row + floorOriginZ);

                poseStack.pushPose();
                poseStack.translate(bx, 0.0f, bz);
                blockRenderer.tesselateBlock(output, 0.0f, 0.0f, 0.0f,
                        fakeLevel, blockPos, blockState, model, blockState.getSeed(blockPos));
                poseStack.popPose();
            }
        }

        // Side objects: rendered one block above the floor, only in outer columns
        if (!sideObjects.isEmpty()) {
            for (int row = 0; row < FLOOR_ROWS; row++) {
                for (int col = 0; col < FLOOR_COLS; col++) {
                    int idx = row * FLOOR_COLS + col;
                    BlockState objState = sideObjects.get(idx % sideObjects.size());
                    if (objState.isAir()) continue;

                    float bx = (col - FLOOR_COLS / 2.0f) * spacing + scrollX;
                    float bz = (row - FLOOR_ROWS / 2.0f) * spacing + scrollZ;

                    BlockStateModel model = modelSet.get(objState);
                    BlockPos blockPos = new BlockPos(col + floorOriginX, 1, row + floorOriginZ);

                    poseStack.pushPose();
                    poseStack.translate(bx, 1.0f, bz);
                    blockRenderer.tesselateBlock(output, 0.0f, 0.0f, 0.0f,
                            fakeLevel, blockPos, objState, model, objState.getSeed(blockPos));
                    poseStack.popPose();
                }
            }
        }

        poseStack.popPose(); // end floor group
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F; // same centering as GuiEntityRenderer
    }

    @Override
    protected String getTextureLabel() {
        return "wolf_floor";
    }

    // Singleton fake level: returns max brightness and default cardinal lighting.
    // Biome tinting is fetched from the player's current position.
    private static final BlockAndTintGetter FULL_BRIGHT_LEVEL = new BlockAndTintGetter() {
        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return 15;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (mc.level == null || player == null) return -1;
            return mc.level.getBlockTint(player.blockPosition(), color);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    };
}
