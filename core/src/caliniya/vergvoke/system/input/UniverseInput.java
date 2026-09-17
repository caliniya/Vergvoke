package caliniya.vergvoke.system.input;

import arc.Events;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.core.Render;
import caliniya.vergvoke.world.stars.Universe;

/**
 * 宇宙视图网格选择器。这是过去的网格实现系统，已经被现在的新图取代了，需要重构<br>
 */
public class UniverseInput implements InputProcessor {

    private final Vec2 world = new Vec2();
    public boolean paused;

    public UniverseInput() {
        Events.run(EventType.events.EnterUV, () -> paused = false);
        Events.run(EventType.events.ExitUV, () -> paused = true);
        paused = true;
    }

    /** 屏幕坐标 → 世界坐标 → 对齐网格 → 更新选中 */
    private void updateSelection(float screenX, float screenY) {

        if (paused)
            return;

        world.set(screenX, screenY);
        Render.universeCamera.unproject(world);

        float gs = 32;
        Universe.selectedX = (float) Math.floor(world.x / gs) * gs;
        Universe.selectedY = (float) Math.floor(world.y / gs) * gs;
        Universe.hasSelection = true;
    }

    // ====== InputProcessor 实现 ======

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        updateSelection(screenX, screenY);
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button) {
        updateSelection(screenX, screenY);
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        updateSelection(screenX, screenY);
        return false;
    }

    // ====== 其余接口空实现 ======

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, KeyCode button) {
        return false;
    }

    @Override
    public boolean keyDown(KeyCode key) {
        return false;
    }

    @Override
    public boolean keyUp(KeyCode key) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }
}
