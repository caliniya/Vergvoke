package caliniya.vergvoke.system.input;

import arc.*;
import arc.input.GestureDetector.GestureListener;
import arc.input.KeyCode;
import arc.input.InputProcessor;
import arc.math.geom.Vec2;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.game.data.CommandData;
import caliniya.vergvoke.game.data.WorldData;

public class UnitControl implements InputProcessor, GestureListener {

    public boolean b = false;

    public UnitControl init() {
        return this;
    }

    @Override
    public boolean tap(float x, float y, int count, KeyCode button) {
        // 桌面端鼠标点击同样会走到这里（SDL 后端把鼠标按下/抬起映射成 pointer=0 的 touch 事件），
        // 所以不再按平台过滤——移动端触控与桌面端点击共用同一套指挥逻辑。
        // 使用全局指挥状态判断
        if (!CommandData.commanding)
            return false;

        Vec2 worldPos = Core.camera.unproject(x, y);
        float wx = worldPos.x;
        float wy = worldPos.y;

        b = false;

        CommandData.findUnit(
                wx,
                wy,
                t -> {
                    if (t == null)
                        return;
                    toggleUnitSelection(t);
                });

        if (b) {
            // 选中变化，刷新指挥面板
            UI.hud.refreshCommand();
            return true;
        }

        // 按当前指挥状态执行（直接指挥）
        if (CommandData.commandType == CommandData.CommandType.Move) {
            if (!CommandData.checkedUnits.isEmpty()) {
                issueMoveCommand(wx, wy);
            }
            return true;
        } else if (CommandData.commandType == CommandData.CommandType.Stop) {
            stopUnits();
            return true;
        }

        return false;
    }

    /** 让选中单位立即停下（清目标/速度/寻路）。 */
    private void stopUnits() {
        synchronized (WorldData.moveunits) {
            for (Unit u : CommandData.checkedUnits) {
                if (u == null)
                    continue;
                u.speedX = 0;
                u.speedY = 0;
                u.targetX = u.x;
                u.targetY = u.y;
                u.path = null;
                u.pathed = false;
                WorldData.moveunits.remove(u);
            }
        }
    }

    private void toggleUnitSelection(Unit u) {
        // 直接操作全局列表
        if (CommandData.checkedUnits.contains(u)) {
            u.isSelected = false;
            CommandData.checkedUnits.remove(u);
        } else {
            u.isSelected = true;
            CommandData.checkedUnits.add(u);
        }
        b = true;
    }

    /** 下达移动指令 */
    private void issueMoveCommand(float tx, float ty) {
        float mapWidth = WorldData.world.W * WorldData.TILE_SIZE;
        float mapHeight = WorldData.world.H * WorldData.TILE_SIZE;

        if (tx < 0 || ty < 0 || tx >= mapWidth || ty >= mapHeight)
            return;
        if (isSolidAtWorldPos(tx, ty))
            return;

        synchronized (WorldData.moveunits) {
            for (int i = 0; i < CommandData.checkedUnits.size; i++) {
                Unit u = CommandData.checkedUnits.get(i);
                if (u == null || u.health <= 0)
                    continue;

                u.targetX = tx;
                u.targetY = ty;

                if (!WorldData.moveunits.array.contains(u)) {
                    WorldData.moveunits.add(u);
                }
                u.pathed = false;
            }
        }
    }

    private boolean isSolidAtWorldPos(float wx, float wy) {
        int gx = (int) (wx / WorldData.TILE_SIZE);
        int gy = (int) (wy / WorldData.TILE_SIZE);
        return WorldData.world.isSolid(gx, gy);
    }

    // InputProcessor 接口的空实现...
    @Override
    public boolean touchDown(int x, int y, int p, KeyCode b) {
        return false;
    }

    @Override
    public boolean pinch(Vec2 i1, Vec2 i2, Vec2 p1, Vec2 p2) {
        return false;
    }

    @Override
    public boolean longPress(float x, float y) {
        return false;
    }

    @Override
    public boolean fling(float vx, float vy, KeyCode button) {
        return false;
    }

    @Override
    public boolean pan(float x, float y, float dx, float dy) {
        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, KeyCode button) {
        return false;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        return false;
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, KeyCode button) {
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

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }
}
