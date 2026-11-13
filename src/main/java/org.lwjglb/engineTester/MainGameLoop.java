package org.lwjglb.engineTester;

import static org.lwjgl.glfw.GLFW.*;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjglb.entities.Camera;
import org.lwjglb.entities.Entity;
import org.lwjglb.entities.Light;
import org.lwjglb.entities.Player;
import org.lwjglb.fontMeshCreator.FontType;
import org.lwjglb.fontMeshCreator.GUIText;
import org.lwjglb.fontRendering.TextMaster;
import org.lwjglb.glb.GlbModel;
import org.lwjglb.guis.GuiRenderer;
import org.lwjglb.guis.GuiTexture;
import org.lwjglb.md3.MD3Model;
import org.lwjglb.md3.SimpleShader;
import org.lwjglb.models.TexturedModel;
import org.lwjglb.normalMappingObjConverter.NormalMappedObjLoader;
import org.lwjglb.particles.Particle;
import org.lwjglb.particles.ParticleMaster;
import org.lwjglb.particles.ParticleSystem;
import org.lwjglb.particles.ParticleTexture;
import org.lwjglb.renderEngine.*;
import org.lwjglb.terrains.Terrain;
import org.lwjglb.textures.ModelTexture;
import org.lwjglb.textures.TerrainTexture;
import org.lwjglb.textures.TerrainTexturePack;
import org.lwjglb.util.Config;
import org.lwjglb.util.Maths;

import javax.imageio.IIOException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainGameLoop {
	private static SimpleShader shader;

	public static void main(String[] args) {
		DisplayManager.createDisplay();
		Loader loader = new Loader();
		TextMaster.init(loader);
		MasterRenderer masterRenderer =	new MasterRenderer(loader);

		List<Entity> entities = new ArrayList<>();
		List<Entity> normalMapEntities = new ArrayList<>();
		List<Terrain> terrains = new ArrayList<Terrain>();
		List<Light> lights = new ArrayList<>();

		Light light = new Light(new Vector3f(10000,10000,10000), new Vector3f(1,1,1));
		lights.add(light);

		TexturedModel playerModel = loader.createTexturedModel(
				"person",
				"playerTexture",
				10,
				1
		);

		Player player = new Player(
				playerModel,
				new Vector3f(0, 0, 0),
				0,
				180,
				0,
				0.5f
		);

		entities.add(player);

		Camera camera =	new Camera(player);

		TerrainTexture backgroundTexture = new TerrainTexture(loader.loadTexture("grassy"));
		TerrainTexture rTexture = new TerrainTexture(loader.loadTexture("mud"));
		TerrainTexture gTexture = new TerrainTexture(loader.loadTexture("grass"));
		TerrainTexture bTexture = new TerrainTexture(loader.loadTexture("path"));
		TerrainTexturePack texturePack = new TerrainTexturePack(backgroundTexture, rTexture, gTexture, bTexture);

		TerrainTexture blendMapLake = new TerrainTexture(loader.loadTexture("blendMapLake"));
		Terrain terrain = new Terrain(0, -1, loader, texturePack, blendMapLake, "heightMapLake");
		terrains.add(terrain);

		TexturedModel pineModel = loader.createTexturedModel("pine",
				"pine",
				10,
				0.5f);

		TexturedModel fernModel = loader.createTexturedModel("fern",
				"fern4",2, 1, 0, true, false);
		TexturedModel grassModel = loader.createTexturedModel("grassModel",
				"grassTexture",
				1, 0, true, false);

		Random random = new Random(5666778);
		for (int i = 0; i < 60; i++) {
			if (i % 3 == 0) {
				float x = random.nextFloat() * 150;
				float z = random.nextFloat() * -150;
				if((x > 50 && x < 100) || (z < -50 && z > -100)) {
				} else {
					float y = terrain.getHeightOfTerrain(x,z);

					entities.add(new Entity(fernModel, 3, new Vector3f(x,y,z), 0, random.nextFloat() * 360, 0, 0.9f));
				}
			}
			if (i % 2 == 0) {
				float x = random.nextFloat() * 150;
				float z = random.nextFloat() * -150;
				if((x > 50 && x < 100) || (z < -50 && z > -100)) {
				} else {
					float y = terrain.getHeightOfTerrain(x, z);
					entities.add(new Entity(pineModel, 1, new Vector3f(x, y, z), 0, random.nextFloat() * 360, 0, random.nextFloat() * 0.6f + 0.8f));
				}
			}
		}

		entities.add(new Entity(grassModel, new Vector3f(15, 6.6f, -55), 0, 0, 0, 1.0f));

		TexturedModel barrelModel = new TexturedModel(NormalMappedObjLoader.loadOBJ("barrel", loader),
				new ModelTexture(loader.loadTexture("barrel")));
		barrelModel.getTexture().setNormalMap(loader.loadTexture("barrelNormal"));
		barrelModel.getTexture().setShineDamper(10);
		barrelModel.getTexture().setReflectivity(0.5f);

		TexturedModel crateModel = new TexturedModel(NormalMappedObjLoader.loadOBJ("crate", loader),
				new ModelTexture(loader.loadTexture("crate")));
		crateModel.getTexture().setNormalMap(loader.loadTexture("crateNormal"));
		crateModel.getTexture().setShineDamper(10);
		crateModel.getTexture().setReflectivity(0.5f);

		TexturedModel boulderModel = new TexturedModel(NormalMappedObjLoader.loadOBJ("boulder", loader),
				new ModelTexture(loader.loadTexture("boulder")));
		boulderModel.getTexture().setNormalMap(loader.loadTexture("boulderNormal"));
		boulderModel.getTexture().setShineDamper(10);
		boulderModel.getTexture().setReflectivity(0.5f);

		Entity entity = new Entity(barrelModel, new Vector3f(65, 10, -75), 0, 0, 0, 1f);
		Entity entity2 = new Entity(boulderModel, new Vector3f(80, 10, -75), 0, 0, 0, 1f);
		Entity entity3 = new Entity(crateModel, new Vector3f(50, 10, -75), 0, 0, 0, 0.04f);
		normalMapEntities.add(entity);
		normalMapEntities.add(entity2);
		normalMapEntities.add(entity3);

		FontType font = new FontType(loader.loadTexture("candara"),
				new File(new Config().getPath() + "assets\\candara.fnt"));

		GUIText text = new GUIText("Este es un texto", 3, font, new Vector2f(0.0f, 0.4f),
				1f, true);
		text.setColour(1, 1, 1);

		List<GuiTexture> guis = new ArrayList<>();
		GuiTexture gui1 = new GuiTexture(loader.loadTexture("item_espada"),
				new Vector2f(-0.85f, 0.8f), new Vector2f(0.1f, 0.14f));
		guis.add(gui1);

		GuiTexture gui2 = new GuiTexture(loader.loadTexture("item_corazon"),
				new Vector2f(-0.65f,0.8f), new Vector2f(0.1f, 0.14f));
		guis.add(gui2);

		GuiTexture gui3 = new GuiTexture(loader.loadTexture("item_posima_verde"),
				new Vector2f(-0.45f, 0.8f), new Vector2f(0.1f, 0.14f));
		guis.add(gui3);

		GuiTexture gui4 = new GuiTexture(loader.loadTexture("item_libro"),
				new Vector2f(-0.85f, -0.8f), new Vector2f(0.1f, 0.14f));
		guis.add(gui4);

		GuiTexture gui5 = new GuiTexture(loader.loadTexture("nivel_vida"),
				new Vector2f(0.8f, 0.9f), new Vector2f(0.2f, 0.2f));
		guis.add(gui5);

		GuiRenderer guiRenderer = new GuiRenderer(loader);

		ParticleMaster.init(loader, masterRenderer.getProjectionMatrix());

		ParticleTexture particleStarTexture = new ParticleTexture(loader.loadTexture("particleStar"), 1);
		ParticleTexture particleAtlasTexture = new ParticleTexture(loader.loadTexture("particleAtlas"), 4);
		ParticleTexture particleCosmicTexture = new ParticleTexture(loader.loadTexture("cosmic"), 4);
		ParticleTexture particleSmokeTexture = new ParticleTexture(loader.loadTexture("Smoke45Frames"), 7);
		ParticleTexture particleFireTexture = new ParticleTexture(loader.loadTexture("efecto6"), 4);

		ParticleSystem system = new ParticleSystem(particleStarTexture, 40, 10, 0.1f, 1, 1.6f);
		system.setLifeError(0.1f);
		system.setSpeedError(0.25f);
		system.setScaleError(0.5f);
		system.randomizeRotation();

		ParticleSystem systemAtlas = new ParticleSystem(particleAtlasTexture, 40, 2, 0.1f, 2, 1.5f);
		systemAtlas.randomizeRotation();
		systemAtlas.setDirection(new Vector3f(0f, 1, 0), 0.1f);
		systemAtlas.setLifeError(0.1f);
		systemAtlas.setSpeedError(0.25f);
		systemAtlas.setScaleError(1.5f);
		Vector3f atlasPosition = new Vector3f(15.0f, 20.0f, -20f);

		ParticleSystem fireSystem = new ParticleSystem(particleFireTexture, 40, 0.4f, -0.2f, 1.5f, 1.5f);
		fireSystem.randomizeRotation();
		fireSystem.setLifeError(0.5f);
		fireSystem.setSpeedError(0.25f);
		fireSystem.setScaleError(0.5f);
		Vector3f firePosition = new Vector3f(17f, 6f, -25);

		ParticleSystem smokeSystem = new ParticleSystem(particleSmokeTexture, 50, 1.5f, 0.005f, 1.2f, 3f);
		smokeSystem.setDirection(new Vector3f(0, 1, 0), 0.3f);
		smokeSystem.setLifeError(3.1f);
		smokeSystem.setSpeedError(1.25f);
		smokeSystem.setScaleError(0f);
		smokeSystem.randomizeRotation();
		Vector3f smokePosition = new Vector3f(8.0f, 15.0f, -40f);

		ParticleSystem cosmicSystem = new ParticleSystem(particleCosmicTexture, 50, 0.6f, 0.3f, 1.5f, 100);
		cosmicSystem.setDirection(new Vector3f(0, 1, 0), 0.8f);
		cosmicSystem.setLifeError(0.1f);
		cosmicSystem.setSpeedError(0.25f);
		cosmicSystem.setScaleError(0.5f);
		cosmicSystem.randomizeRotation();
		Vector3f cosmicPosition = new Vector3f(30.0f, 20.0f, -38);

		MD3Model md3_1;
		String nombre = "bender";
		md3_1 = new MD3Model();
		md3_1.loadModel(new Config().getPath() + "md3/" + nombre, nombre);
		md3_1.setTorsoAnimation("TORSO_ATTACK");
		md3_1.setLegsAnimation("LEGS_JUMP");
		float rotaModel = 0f;
		shader = new SimpleShader();

		GlbModel model;
		float modelAngle = 0.0f;

		try {
			model = GlbModel.load("src/main/resources/models/chaos.glb");
		} catch (IOException e) {
			throw new RuntimeException("No fue posible cargar el modelo GLB usando Assimp: " +
					"src/main/resources/models/chaos.glb", e);

		}

		model.crearShader();
		model.setActiveAnimationIndex(1);

		while (!glfwWindowShouldClose(DisplayManager.window)) {
			player.move(terrain);

			Matrix4f  modelMatrix = new Matrix4f()
					.translate(
							player.getPosition().x,
								player.getPosition().y+3,
							player.getPosition().z
					)
					.rotateY((float) Math.toRadians(-90 + player.getRotY()))
					.scale(0.1f);
			camera.move();

			system.generateParticles(player.getPosition());
			smokeSystem.generateParticles(smokePosition);
			fireSystem.generateParticles(firePosition);
			cosmicSystem.generateParticles(cosmicPosition);
			systemAtlas.generateParticles(atlasPosition);
			ParticleMaster.update(camera);

			entity.increaseRotation(0.1f, 0.2f, 0.3f);
			entity2.increaseRotation(0.3f, 0.1f, 0.2f);
			entity3.increaseRotation(0.2f, 0.3f, 0.1f);

			masterRenderer.renderScene(
					entities,
					normalMapEntities,
					terrains,
					lights,
					camera,
					new Vector4f(0,-1,0,100000));

			md3_1.drawMD3(shader, masterRenderer, camera, modelMatrix);

			model.update(0.002f);
			Matrix4f vp = new Matrix4f(masterRenderer.getProjectionMatrix()).mul(Maths.createViewMatrix(camera));
			Matrix4f glbModelMatrix = new Matrix4f()
					.identity()
					.translate(
								player.getPosition().x-5,
								player.getPosition().y,
								player.getPosition().z
							)
							.scale(model.getRecommendedScale())
							.rotateY((float) Math.toRadians((modelAngle)));

			model.drawGLB(model, vp, glbModelMatrix, masterRenderer, camera);

			ParticleMaster.renderParticles(camera);
			guiRenderer.render(guis);
			TextMaster.render();
			DisplayManager.updateDisplay();
		}
		ParticleMaster.cleanUp();
		guiRenderer.cleanUp();
		masterRenderer.cleanUp();
		loader.cleanUp();
		DisplayManager.closeDisplay();
	}

}