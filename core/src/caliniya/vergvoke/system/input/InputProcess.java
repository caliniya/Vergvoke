package caliniya.vergvoke.system.input;

import arc.*;
import arc.input.*;
import arc.input.GestureDetector.GestureListener;
import arc.math.geom.*;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.world.stars.Universe;

/**
 * 输入处理基类：平台无关的输入全部写在这里——
 *
 * <ul>
 *   <li>单位指挥：选中 / 下令 / 中断；
 *   <li>相机：游戏相机与宇宙相机的拖拽平移、滚轮 / 捏合缩放、WASD 键盘移动；
 *   <li>宇宙视图：网格选择（鼠标移动 / 触摸）。
 * </ul>
 *
 * <p>平台差异只留在子类（{@link DesktopInput} / {@link MobileInput}）的覆盖方法里，
 * 结构参考 Mindustry 的 InputHandler / DesktopInput / MobileInput。
 *
 * <p>原来的 CameraInput / UniverseCameraInput / UniverseInput 全并进来了：
 * 运行状态只剩这一个对象的 {@link #inUniverse}（宇宙视图）和 {@link #paused}（游戏暂停），
 * 不用再给每个输入处理器各维护一份"运行 / 暂停"。
 *
 * <p>{@link InputProcessor} 与 {@link GestureListener} 的方法都是 default，子类按需覆盖。
 *
 * <p>接线：Vergvoke 里按平台二选一创建，挂到 {@link InputMultiplexer} 上（事件），
 * 并由主循环每帧调用 {@link #update(float)}（键盘移动 / 宇宙相机视口）。
 */
public abstract class InputProcess implements InputProcessor, GestureListener {

    // ===== 相机：共享状态 =====

    /** WASD / 方向键按住标志（两种视图共用）。 */
    private boolean up, down, left, right;

    /** 键盘平移速度（像素/帧，随当前缩放放大）。 */
    private float keySpeed = 10f;

    /** 捏合缩放开始前的缩放快照（touchDown 时记录）。 */
    private float lastZoomSnapshot = 1f;

    /** 网格选择用的反投影临时向量（复用，避免每次事件都分配）。 */
    private final Vec2 universePos = new Vec2();

    /** 宇宙视图的网格大小。 */
    private static final float GRID_SIZE = 32f;

    /** 是否处于宇宙视图（EnterUV / ExitUV 切换）：决定输入作用在哪台相机上。 */
    protected boolean inUniverse;

    /** 游戏是否暂停（GamePause）：暂停时不响应相机类输入。 */
    protected boolean paused;

    public InputProcess() {
        Events.on(EventType.GamePause.class, event -> paused = event.pause);
        Events.run(EventType.events.EnterUV, () -> inUniverse = true);
        Events.run(EventType.events.ExitUV, () -> inUniverse = false);
    }

    // ===== 每帧：键盘移动 / 宇宙相机视口 =====

    /** 每帧由主循环驱动：WASD 平移相机，宇宙视图下同步缩放后的视口尺寸。 */
    public void update(float delta) {
        if (paused)
            return;

        // 帧时间乘进来（60TPS 时 delta ≈ 1，和以前的移动手感一致）
        float speed = keySpeed * delta * (Core.input.keyDown(KeyCode.shiftLeft) ? 2f : 1f);

        if (inUniverse) {
            speed *= Render.universeZoom;

            if (up)
                Render.universeCamera.position.y += speed;
            if (down)
                Render.universeCamera.position.y -= speed;
            if (left)
                Render.universeCamera.position.x -= speed;
            if (right)
                Render.universeCamera.position.x += speed;

            // 应用缩放：宇宙相机视口 = 屏幕尺寸 × 缩放
            Render.universeCamera.width = Core.graphics.getWidth() * Render.universeZoom;
            Render.universeCamera.height = Core.graphics.getHeight() * Render.universeZoom;
        } else {
            speed *= Render.currentZoom;

            if (up)
                Core.camera.position.y += speed;
            if (down)
                Core.camera.position.y -= speed;
            if (left)
                Core.camera.position.x -= speed;
            if (right)
                Core.camera.position.x += speed;
        }
    }

    // ===== 相机手势 =====

    /** 拖拽平移：宇宙视图挪宇宙相机，否则挪游戏相机。 */
    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        if (paused)
            return false;

        if (inUniverse) {
            Render.universeCamera.position.x -= deltaX * Render.universeZoom;
            Render.universeCamera.position.y -= deltaY * Render.universeZoom;
        } else {
            Core.camera.position.x -= deltaX * Render.currentZoom;
            Core.camera.position.y -= deltaY * Render.currentZoom;
        }
        return false;
    }

    /** 按下时记下缩放起点，作为捏合缩放的基准。 */
    @Override
    public boolean touchDown(float x, float y, int pointer, KeyCode button) {
        if (paused)
            return false;

        lastZoomSnapshot = inUniverse ? Render.universeZoom : Render.currentZoom;
        return false;
    }

    /** 捏合缩放（移动端双指）。 */
    @Override
    public boolean zoom(float initialDistance, float distance) {
        if (paused || initialDistance == 0)
            return false;

        float ratio = initialDistance / distance;

        if (inUniverse) {
            Render.setUniverseZoom(lastZoomSnapshot * ratio);
        } else {
            Render.setZoom(lastZoomSnapshot * ratio);
        }
        return true;
    }

    /** 滚轮缩放（桌面端）。 */
    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (paused)
            return false;

        if (inUniverse) {
            Render.zoomUniverse(amountY * 0.1f * Render.universeZoom);
        } else {
            Render.zoom(amountY * 0.1f * Render.currentZoom);
        }
        return true;
    }

    @Override
    public boolean keyDown(KeyCode key) {
        if (paused)
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
        if (paused)
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

    // ===== 宇宙视图：网格选择 =====

    /** 屏幕坐标 → 宇宙世界坐标 → 对齐网格 → 更新选中（只在宇宙视图生效）。 */
    protected void updateUniverseSelection(float screenX, float screenY) {
        if (!inUniverse)
            return;

        universePos.set(screenX, screenY);
        Render.universeCamera.unproject(universePos);

        Universe.selectedX = (float) Math.floor(universePos.x / GRID_SIZE) * GRID_SIZE;
        Universe.selectedY = (float) Math.floor(universePos.y / GRID_SIZE) * GRID_SIZE;
        Universe.hasSelection = true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, KeyCode button) {
        updateUniverseSelection(screenX, screenY);
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        updateUniverseSelection(screenX, screenY);
        return false;
    }

    /** 桌面端鼠标移动：宇宙视图下悬停即选中网格。 */
    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        updateUniverseSelection(screenX, screenY);
        return false;
    }

    // ===== 平台无关：选中 =====

    /** 点世界坐标：命中单位则切换选中。 @return 选中列表是否发生变化 */
    protected boolean selectAt(float wx, float wy) {
        int before = CommandData.checkedUnits.size;
        CommandData.findUnit(
                wx,
                wy,
                t -> {
                    if (t == null)
                        return;
                    toggleUnitSelection(t);
                });
        return CommandData.checkedUnits.size != before;
    }

    /** 切换单个单位的选中状态。 */
    protected void toggleUnitSelection(Unit u) {
        if (CommandData.checkedUnits.contains(u)) {
            u.isSelected = false;
            CommandData.checkedUnits.remove(u);
        } else {
            u.isSelected = true;
            CommandData.checkedUnits.add(u);
        }
    }

    /** 清空选择（复用 HUD 的"清空"：复位标记 + 刷新面板）。 */
    protected void clearSelection() {
        UI.hud.clearSelection();
    }

    // ===== 平台无关：下令 =====

    /** 执行当前指令：Move → 移动到点击点；Stop → 原地停下。 @return 是否执行了指令 */
    protected boolean executeSelected(float wx, float wy) {
        if (CommandData.checkedUnits.isEmpty())
            return false;

        if (CommandData.commandType == CommandData.CommandType.Move) {
            issueMoveCommand(wx, wy);
            return true;
        } else if (CommandData.commandType == CommandData.CommandType.Stop) {
            stopUnits();
            return true;
        }
        return false;
    }

    /** 中断选中单位当前的操作（清目标 / 速度 / 寻路 = 立刻停下）。 */
    protected boolean interruptSelected() {
        if (CommandData.checkedUnits.isEmpty())
            return false;

        stopUnits();
        return true;
    }

    /** 让选中单位立即停下（清目标/速度/寻路）。 */
    protected void stopUnits() {
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

    /** 下达移动指令。 */
    protected void issueMoveCommand(float tx, float ty) {
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
}
