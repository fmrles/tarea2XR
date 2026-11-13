package org.lwjglb.renderEngine;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class DisplayManager {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final String TITLE = "OpenGL 3D Game Engine";
    private static Keyboard keyboard = new Keyboard();  

    private static long lastFrameTime;
    private static float delta;

    public static long window;

    public static void createDisplay() {    
        System.out.println("LWJGL " + Version.getVersion());

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints(); 
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);      
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);      
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); 
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); 

        window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetKeyCallback(window, new Keyboard()); 

        Mouse.createCallbacks();


        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); 
            IntBuffer pHeight = stack.mallocInt(1); 

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2
            );
        } 

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        glfwShowWindow(window);

        GL.createCapabilities();

        lastFrameTime = getCurrentTime();
    }

    public static void updateDisplay() {    
        glfwSwapBuffers(window); 
        glfwPollEvents();

        long currentFrameTime = getCurrentTime();
        delta = (currentFrameTime - lastFrameTime)/1000f;
        lastFrameTime = currentFrameTime;

    }

    public static int getWindowWidth() {    
        IntBuffer w = BufferUtils.createIntBuffer(1);
        glfwGetWindowSize(window, w, null);
        return w.get(0);
    }

    public static int getWindowHeight() {   
        IntBuffer h = BufferUtils.createIntBuffer(1);
        glfwGetWindowSize(window, null, h);
        return h.get(0);
    }

    public static long getWindow() {
        return window;
    }

    public static void closeDisplay() {     
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private static long getCurrentTime() {
        return (long) (GLFW.glfwGetTime() * 1000);
    }

    public static float getFrameTimeSeconds() {
        return delta;
    }



}