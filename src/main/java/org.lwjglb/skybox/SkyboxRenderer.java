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
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapOf(zoneFrom));
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapOf(zoneTo));
        shader.loadBlendFactor(zoneBlend);
    }

    private int cubemapOf(int zone) {
        return switch (zone) {
            case 1 -> dayTexture;
            case 2 -> nightTexture;
            default -> morningTexture;
        };
    }

}
