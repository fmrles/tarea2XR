package org.lwjglb.skybox;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjglb.entities.Camera;
import org.lwjglb.models.RawModel;
import org.lwjglb.renderEngine.Loader;

/**
 * Renders the skybox cube around the camera with a per-zone cubemap (block 14),
 * crossfading between zones at boundaries (block 14b).
 *
 * <p>Zone A (0) = morning, zone B (1) = day, zone C (2) = night — each backed by
 * its own cubemap generated from the equirectangular panoramas in
 * {@code skybox_assets} by {@code tools.EquirectToCube}. Block 14b replaces the
 * block-14 HARD SWITCH: {@code render(...)} receives the fade state
 * (zoneFrom, zoneTo, zoneBlend) from MasterRenderer — the single source of
 * truth for the transition — and {@link #bindTextures(int, int, float)} binds
 * {@code texture1 = cubemapOf(zoneFrom)}, {@code texture2 = cubemapOf(zoneTo)}
 * with the given blendFactor, so the untouched {@code skyboxFragmentShader}'s
 * {@code mix(texture1, texture2, blendFactor)} crossfades the two cubemaps.
 * The per-zone fog is lerped IN SYNC by MasterRenderer (same zoneBlend), so
 * the sky and fog/clearColor never pop. The old time-based blend (which forced
 * {@code time = 12000} and pinned every branch to the night cubemap — the
 * always-night bug) is removed as dead code.
 */
public class SkyboxRenderer {

    private static final float SIZE = 150f;

    private static final float[] VERTICES = {
            -SIZE,  SIZE, -SIZE,
            -SIZE, -SIZE, -SIZE,
            SIZE, -SIZE, -SIZE,
            SIZE, -SIZE, -SIZE,
            SIZE,  SIZE, -SIZE,
            -SIZE,  SIZE, -SIZE,

            -SIZE, -SIZE,  SIZE,
            -SIZE, -SIZE, -SIZE,
            -SIZE,  SIZE, -SIZE,
            -SIZE,  SIZE, -SIZE,
            -SIZE,  SIZE,  SIZE,
            -SIZE, -SIZE,  SIZE,

            SIZE, -SIZE, -SIZE,
            SIZE, -SIZE,  SIZE,
            SIZE,  SIZE,  SIZE,
            SIZE,  SIZE,  SIZE,
            SIZE,  SIZE, -SIZE,
            SIZE, -SIZE, -SIZE,

            -SIZE, -SIZE,  SIZE,
            -SIZE,  SIZE,  SIZE,
            SIZE,  SIZE,  SIZE,
            SIZE,  SIZE,  SIZE,
            SIZE, -SIZE,  SIZE,
            -SIZE, -SIZE,  SIZE,

            -SIZE,  SIZE, -SIZE,
            SIZE,  SIZE, -SIZE,
            SIZE,  SIZE,  SIZE,
            SIZE,  SIZE,  SIZE,
            -SIZE,  SIZE,  SIZE,
            -SIZE,  SIZE, -SIZE,

            -SIZE, -SIZE, -SIZE,
            -SIZE, -SIZE,  SIZE,
            SIZE, -SIZE, -SIZE,
            SIZE, -SIZE, -SIZE,
            -SIZE, -SIZE,  SIZE,
            SIZE, -SIZE,  SIZE
    };

    // Faces in Loader.loadCubeMap order: {+X, -X, +Y, -Y, +Z, -Z}
    // = {Right, Left, Top, Bottom, Back, Front}.
    private static final String[] MORNING_TEXTURE_FILES = {
            "skyboxMorningRight", "skyboxMorningLeft", "skyboxMorningTop",
            "skyboxMorningBottom", "skyboxMorningBack", "skyboxMorningFront"
    };

    private static final String[] DAY_TEXTURE_FILES = {
            "skyboxDayRight", "skyboxDayLeft", "skyboxDayTop",
            "skyboxDayBottom", "skyboxDayBack", "skyboxDayFront"
    };

    private static final String[] NIGHT_TEXTURE_FILES = {
            "skyboxNightRight", "skyboxNightLeft", "skyboxNightTop",
            "skyboxNightBottom", "skyboxNightBack", "skyboxNightFront"
    };

    private RawModel cube;
    private int morningTexture;
    private int dayTexture;
    private int nightTexture;
    private SkyboxShader shader;

    public SkyboxRenderer(Loader loader, Matrix4f projectionMatrix) {
        cube = loader.loadToVAO(VERTICES, 3);
        morningTexture = loader.loadCubeMap(MORNING_TEXTURE_FILES);
        dayTexture = loader.loadCubeMap(DAY_TEXTURE_FILES);
        nightTexture = loader.loadCubeMap(NIGHT_TEXTURE_FILES);
        shader = new SkyboxShader();
        shader.start();
        shader.connectTextureUnits();
        shader.loadProjectionMatrix(projectionMatrix);
        shader.stop();
    }

    //public void render(Camera camera, Vector3f skyColor) {
    //    render(camera, skyColor.x, skyColor.y, skyColor.z);
    //}

    /**
     * Renders the skybox, crossfading from {@code zoneFrom}'s cubemap to
     * {@code zoneTo}'s cubemap as {@code zoneBlend} goes 0 -> 1. MasterRenderer
     * owns the fade state and passes it every frame (single source of truth;
     * there is no setZone on this class anymore).
     */
    public void render(Camera camera, float r, float g, float b,
                       int zoneFrom, int zoneTo, float zoneBlend) {
        shader.start();
        shader.loadViewMatrix(camera);
        shader.loadFogColor(r, g, b);
        GL30.glBindVertexArray(cube.getVaoID());
        GL20.glEnableVertexAttribArray(0);
        bindTextures(zoneFrom, zoneTo, zoneBlend);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, cube.getVertexCount());
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        shader.stop();
    }


    private void bindTextures(int zoneFrom, int zoneTo, float zoneBlend) {
        // Block 14b: crossfade — texture1 = zoneFrom cubemap, texture2 = zoneTo
        // cubemap, blendFactor = zoneBlend, so the shader's
        // mix(texture1, texture2, blendFactor) blends the two zones. When
        // zoneBlend == 1 (idle) both slots are the SAME cubemap, which
        // reproduces the old hard switch exactly.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapOf(zoneFrom));
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapOf(zoneTo));
        shader.loadBlendFactor(zoneBlend);
    }

    /** Cubemap texture ID for the given zone (0 = A/morning, 1 = B/day, 2 = C/night). */
    private int cubemapOf(int zone) {
        return switch (zone) {
            case 1 -> dayTexture;
            case 2 -> nightTexture;
            default -> morningTexture;
        };
    }

}
