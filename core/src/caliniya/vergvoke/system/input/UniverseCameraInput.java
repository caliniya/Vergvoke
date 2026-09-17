package caliniya.vergvoke.system.input;

import arc.Core;
import arc.Events;
import arc.input.GestureDetector.GestureListener;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.core.Render;
import caliniya.vergvoke.system.System;

/**
 * 宇宙视图相机的输入控制。<br>
 * 无边界限制，WASD/方向键移动，滚轮缩放，拖拽平移。<br>
 * 仅在宇宙视图激活时响应。
 */
public class UniverseCameraInput extends System<UniverseCameraInput>
        implements GestureListener, InputProcessor {

    private boolean up, down, left, right;
    private float keySpeed = 10f;
    private float lastZoomSnapshot = 1f;

    /** 宇宙相机自身缩放（供外部读取） */
    public static float zoom = 1f;

    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 2f;

    @Override
    public UniverseCameraInput init() {
        this.index = 2;
        Events.run(EventType.events.EnterUV, () -> paused = false);
        Events.run(EventType.events.ExitUV, () -> paused = true);
        paused = true;
        return super.init(false, false);
    }

    @Override
    public void update() {
        if (!inited || paused)
            return;

        float speed = keySpeed * zoom * (Core.input.keyDown(KeyCode.shiftLeft) ? 2f : 1f);

        if (up)
            Render.universeCamera.position.y += speed;
        if (down)
            Render.universeCamera.position.y -= speed;
        if (left)
            Render.universeCamera.position.x -= speed;
        if (right)
            Render.universeCamera.position.x += speed;

        // 应用缩放
        float targetW = Core.graphics.getWidth() * zoom;
        float targetH = Core.graphics.getHeight() * zoom;
        Render.universeCamera.width = targetW;
        Render.universeCamera.height = targetH;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        if (!inited || paused)
            return false;
        Render.universeCamera.position.x -= deltaX * zoom;
        Render.universeCamera.position.y -= deltaY * zoom;
        return false;
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, KeyCode button) {
        if (!inited || paused)
            return false;
        lastZoomSnapshot = zoom;
        return false;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        if (!inited || paused)
            return false;
        if (initialDistance == 0)
            return false;
        float ratio = initialDistance / distance;
        zoom = Mathf.clamp(lastZoomSnapshot * ratio, MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!inited || paused)
            return false;
        float zoomSpeed = 0.1f * zoom;
        zoom = Mathf.clamp(zoom + amountY * zoomSpeed, MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    @Override
    public boolean keyDown(KeyCode key) {
        if (!inited || paused)
            return false;
        if (key == KeyCode.w || key == KeyCode.up)
            up = true;
        if (key == KeyCode.s || key == KeyCode.down)
            down = true;
        if (key == KeyCode.a || key == KeyCode.left)
            left = true;
        if (key == KeyCode.d || key == KeyCode.right)
            right = true;
        return false;
    }

    @Override
    public boolean keyUp(KeyCode key) {
        if (!inited || paused)
            return false;
        if (key == KeyCode.w || key == KeyCode.up)
            up = false;
        if (key == KeyCode.s || key == KeyCode.down)
            down = false;
        if (key == KeyCode.a || key == KeyCode.left)
            left = false;
        if (key == KeyCode.d || key == KeyCode.right)
            right = false;
        return false;
    }

    // ====== 其余接口空实现 ======

    @Override
    public boolean pinch(Vec2 i1, Vec2 i2, Vec2 p1, Vec2 p2) {
        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, KeyCode button) {
        return false;
    }

    @Override
    public boolean longPress(float x, float y) {
        return false;
    }

    @Override
    public boolean fling(float velocityX, float velocityY, KeyCode button) {
        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, KeyCode button) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }
}
