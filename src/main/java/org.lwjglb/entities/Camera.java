package org.lwjglb.entities;

import org.joml.Vector3f;

public class Camera {

    private float distanceFromPlayer = 10; 
    private float angleAroundPlayer = 0;

    private Vector3f position = new Vector3f(0,8f,0);
    private float pitch = 25.0f; 
    private float yaw = 0;
    private float roll;

    private Player player;

    public Camera(Player player) {
        this.player = player;
    }

    public void move() {
        float horizontalDistance = calculateHorizontalDistance();
        float verticalDistance = calculateVerticalDistance();
        calculateCameraPosition(horizontalDistance, verticalDistance);
        this.yaw = 180 - (player.getRotY() + angleAroundPlayer);
    }

    public void invertPitch() {
        this.pitch = -pitch;
    }

    public Vector3f getPosition(){
        return position;
    }

    public void setPositionY(float posY){
        position.y = posY;
    }

    public float getPitch(){
        return pitch;
    }

    public float getYaw(){
        return yaw;
    }

    public float getRoll(){
        return roll;
    }


    private void calculateCameraPosition(float horizDistance, float verticDistance) {
        float theta = player.getRotY() + angleAroundPlayer;
        float offsetX = (float) (horizDistance * Math.sin(Math.toRadians(theta)));
        float offsetZ = (float) (horizDistance * Math.cos(Math.toRadians(theta)));
        position.x = player.getPosition().x - offsetX;
        position.z = player.getPosition().z - offsetZ;
        position.y = player.getPosition().y + verticDistance;
    }


    private float calculateHorizontalDistance() {
        float hD = (float) (distanceFromPlayer * Math.cos(Math.toRadians(pitch)));
        if(hD < 2)
            hD = 2;
        return hD;
    }

    private float calculateVerticalDistance() {
        float vD = (float) (distanceFromPlayer * Math.sin(Math.toRadians(pitch)));
        if(vD < 4)
            vD = 4;
        return vD;
    }

}
