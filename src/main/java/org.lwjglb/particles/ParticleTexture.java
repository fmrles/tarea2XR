package org.lwjglb.particles;

import org.joml.Vector3f;

public class ParticleTexture {

    private int textureID;
    private int numberOfRows;
    private Vector3f colour;

    public ParticleTexture(int textureID, int numberOfRows) {
        this(textureID, numberOfRows, new Vector3f(1f, 1f, 1f));
    }

    public ParticleTexture(int textureID, int numberOfRows, Vector3f colour) {
        super();
        this.textureID = textureID;
        this.numberOfRows = numberOfRows;
        this.colour = colour;
    }

    public int getTextureID() {
        return textureID;
    }

    public int getNumberOfRows() {
        return numberOfRows;
    }

    public Vector3f getColour() {
        return colour;
    }
}
