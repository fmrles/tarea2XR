package org.lwjglb.game;

import org.lwjglb.renderEngine.Keyboard;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Unified input for the runner: gamepad (GLFW joystick) with automatic
 * keyboard fallback when no controller is connected.
 *
 * Gamepad mapping (see design doc, section 3):
 *   Left stick X / D-Pad left-right -> change lane
 *   Button A (Cross)                -> jump
 *   Button B (Square)               -> slide
 *   Button Start                    -> restart after WIN/LOSE
 *
 * Keyboard fallback:
 *   A / D or Arrow Left-Right       -> change lane
 *   W or Space                      -> jump
 *   S or Arrow Down                 -> slide
 *   Enter                           -> restart after WIN/LOSE
 */
public final class GameInput {

    private static final float AXIS_ON = 0.5f;
    private static final float LANE_REPEAT_SECONDS = 0.25f;
    private static final int BUTTON_A = 0;
    private static final int BUTTON_B = 1;
    private static final int BUTTON_START = 9;

    private int laneDelta;
    private boolean jumpPressed;
    private boolean slidePressed;
    private boolean restartPressed;
    private boolean gamepadConnected;

    private float laneCooldown;

    /** Reads the devices and computes the intents for this frame. */
    public void poll(float dt) {
        laneDelta = 0;
        jumpPressed = false;
        slidePressed = false;
        restartPressed = false;
        if (laneCooldown > 0f) {
            laneCooldown -= dt;
        }

        float axisX = 0f;
        boolean dpadLeft = false;
        boolean dpadRight = false;
        boolean buttonA = false;
        boolean buttonB = false;
        boolean buttonStart = false;
        gamepadConnected = false;

        for (int id = 0; id <= GLFW_JOYSTICK_LAST; id++) {
            if (!glfwJoystickPresent(id)) {
                continue;
            }
            FloatBuffer axes = glfwGetJoystickAxes(id);
            if (axes == null || axes.capacity() == 0) {
                continue;
            }
            gamepadConnected = true;
            axisX = axes.get(0);

            ByteBuffer hats = glfwGetJoystickHats(id);
            if (hats != null && hats.capacity() > 0) {
                int hat = hats.get(0);
                dpadLeft = (hat & GLFW_HAT_LEFT) != 0;
                dpadRight = (hat & GLFW_HAT_RIGHT) != 0;
            }

            ByteBuffer buttons = glfwGetJoystickButtons(id);
            if (buttons != null && buttons.capacity() > BUTTON_B) {
                buttonA = buttons.get(BUTTON_A) == GLFW_PRESS;
                buttonB = buttons.get(BUTTON_B) == GLFW_PRESS;
                // Button 9 = Start/Options, used to restart after WIN/LOSE.
                buttonStart = buttons.capacity() > BUTTON_START
                        && buttons.get(BUTTON_START) == GLFW_PRESS;
            }
            break;
        }

        // --- lane change: gamepad first, keyboard as fallback ---
        int direction = 0;
        if (axisX > AXIS_ON || dpadRight || Keyboard.isKeyDown(GLFW_KEY_RIGHT) || Keyboard.isKeyDown(GLFW_KEY_D)) {
            direction = 1;
        } else if (axisX < -AXIS_ON || dpadLeft || Keyboard.isKeyDown(GLFW_KEY_LEFT) || Keyboard.isKeyDown(GLFW_KEY_A)) {
            direction = -1;
        }
        if (direction != 0 && laneCooldown <= 0f) {
            laneDelta = direction;
            laneCooldown = LANE_REPEAT_SECONDS;
        }

        boolean jumpDown = buttonA
                || Keyboard.isKeyDown(GLFW_KEY_SPACE)
                || Keyboard.isKeyDown(GLFW_KEY_W)
                || Keyboard.isKeyDown(GLFW_KEY_UP);
        boolean slideDown = buttonB
                || Keyboard.isKeyDown(GLFW_KEY_S)
                || Keyboard.isKeyDown(GLFW_KEY_DOWN);

        boolean restartDown = buttonStart || Keyboard.isKeyDown(GLFW_KEY_ENTER);

        // Edge triggered: one press = one action.
        jumpPressed = jumpDown && !isJumpHeld;
        slidePressed = slideDown && !isSlideHeld;
        restartPressed = restartDown && !isRestartHeld;
        isJumpHeld = jumpDown;
        isSlideHeld = slideDown;
        isRestartHeld = restartDown;
    }

    private boolean isJumpHeld;
    private boolean isSlideHeld;
    private boolean isRestartHeld;

    public int getLaneDelta() {
        return laneDelta;
    }

    public boolean isJumpPressed() {
        return jumpPressed;
    }

    public boolean isSlidePressed() {
        return slidePressed;
    }

    public boolean isRestartPressed() {
        return restartPressed;
    }

    public boolean isGamepadConnected() {
        return gamepadConnected;
    }
}
