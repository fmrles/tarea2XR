package org.lwjglb.renderEngine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjglb.entities.Entity;
import org.lwjglb.models.RawModel;
import org.lwjglb.models.TexturedModel;
import org.lwjglb.shaders.TerrainShader;
import org.lwjglb.terrains.Terrain;
import org.lwjglb.textures.ModelTexture;
import org.lwjglb.textures.TerrainTexturePack;
import org.lwjglb.util.Maths;

import java.util.List;

public class TerrainRenderer {

    // Block 11: corridor UV mapping for the road texture (b slot).
    // Derived from blendMapPuerto.png (128x128): blue (0,0,255) road texels sit
    // at x = 49..78 inclusive in EVERY row, so the corridor occupies terrain
    // UV.x in [49/128, 79/128) = [0.3828125, 0.6171875), span 30/128 = 0.234375.
    // Terrain UVs run 0..1 across the whole 150-unit patch (Terrain.generateTerrain),
    // so the corridor covers world x [57.42, 92.58], centered exactly on x = 75
    // (lane 1 center).
    // - ROAD_U0 / ROAD_SPAN_U remap terrain UV.x onto texture u 0..1 across the
    //   corridor. Yellow line check: roadCoords.x == 0.5 when terrain UV.x == 0.5
    //   (since 0.5 is the corridor midpoint) -> world x = 75 = lane 1 center, and
    //   the texture's yellow line is centered at u ~= 0.5 (measured px 504..519
    //   of 1024) -> yellow lands on the center lane. The white edge lines
    //   (measured px 81..96 / 927..942 of 1024, u ~= 0.086 / 0.913) land at
    //   world x ~= 60.4 / 89.5, just inside the corridor edges 57.42 / 92.58.
    // - ROAD_V_REPEAT: tile length along z = spanU * SIZE = 0.234375 * 150 =
    //   35.16 u (one full texture per tile, same as the corridor width -> square
    //   world tiles, asphalt grain not stretched), so repeat over the 150-unit
    //   z axis = SIZE / (spanU * SIZE) = 1 / spanU = 4.2667.
    // NOTE: constants are derived from blendMapPuerto; the legacy
    // engineTester/MainGameLoop demo uses a different blend map — acceptable,
    // it is not shipped as the game.
    private static final float ROAD_U0 = 49f / 128f;
    private static final float ROAD_SPAN_U = 30f / 128f;
    private static final float ROAD_V_REPEAT = 1f / ROAD_SPAN_U;

    private TerrainShader shader;

    public TerrainRenderer(TerrainShader shader, Matrix4f projectionMatrix) {
        this.shader = shader;
        shader.start();
        shader.loadProjectionMatrix(projectionMatrix);
        shader.connectTextureUnits();
        shader.loadRoadMapping(ROAD_U0, ROAD_SPAN_U, ROAD_V_REPEAT);
        shader.stop();
    }

    public void render(List<Terrain> terrains) {
        for (Terrain terrain : terrains) {
            prepareTerrain(terrain);
            loadModelMatrix(terrain);
            GL11.glDrawElements(GL11.GL_TRIANGLES, terrain.getModel().getVertexCount(),
                    GL11.GL_UNSIGNED_INT, 0);
            unbindTexturedModel();
        }
    }

    private void prepareTerrain(Terrain terrain) {
        RawModel rawModel = terrain.getModel();
        GL30.glBindVertexArray(rawModel.getVaoID());
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
        bindTextures(terrain);
        shader.loadShineVariables(1, 0);
    }

    private void bindTextures(Terrain terrain) {
        TerrainTexturePack texturePack = terrain.getTexturePack();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texturePack.getBackgroundTexture().getTextureID());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texturePack.getrTexture().getTextureID());
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texturePack.getgTexture().getTextureID());
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texturePack.getbTexture().getTextureID());
        GL13.glActiveTexture(GL13.GL_TEXTURE4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, terrain.getBlendMap().getTextureID());

    }

    private void unbindTexturedModel() {
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL20.glDisableVertexAttribArray(2);
        GL30.glBindVertexArray(0);
    }

    private void loadModelMatrix(Terrain terrain) {
        Matrix4f transformationMatrix = Maths.createTransformationMatrix(
                new Vector3f(terrain.getX(), 0, terrain.getZ()), 0, 0, 0, 1);
        shader.loadTransformationMatrix(transformationMatrix);
    }

}
