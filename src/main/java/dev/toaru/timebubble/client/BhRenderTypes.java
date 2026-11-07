package dev.toaru.timebubble.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom render types for the Time Bubble visual effects.
 * Based on the blackhole addon pattern.
 */
public final class BhRenderTypes extends RenderType {
    private BhRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                          Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new IllegalStateException("not meant to be constructed");
    }

    /** Dark/opaque geometry - the inner sphere and dome surface (flat colored, no glow) */
    public static final RenderType DARK = create("timebubble:dark", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Additive glow geometry - rings, lensing, shockwave (uses vanilla lightning) */
    public static final RenderType GLOW = RenderType.lightning();

    /** Distortion effect - glass-like dome with texture */
    public static final RenderType DISTORTION = create("timebubble:distortion", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_TEX_COLOR_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("timebubble", "textures/misc/distortion.png"), false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** Entity outline - faint blue glow around affected entities */
    public static final RenderType OUTLINE = create("timebubble:outline", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static RenderType create(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, RenderType.CompositeState state) {
        return new RenderType(name, format, mode, size, crumbling, sort, () -> {}, () -> {}) {
            @Override
            public RenderType.CompositeState state() { return state; }
        };
    }
}