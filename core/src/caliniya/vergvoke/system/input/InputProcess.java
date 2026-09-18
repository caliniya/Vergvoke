package caliniya.vergvoke.system.input;

import arc.input.GestureDetector.GestureListener;
import arc.input.InputProcessor;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.game.data.CommandData;
import caliniya.vergvoke.game.data.WorldData;

/**
 * 输入处理器基类：装"平台无关的意图"——选中 / 下令 / 中断；
 * 平台子类（{@link DesktopInput} / {@link MobileInput}）只负责把手势翻译成这些意图。
 *
 * <p>
 * 结构参考 Mindustry 的 InputHandler / DesktopInput / MobileInput：共享逻辑在基类、平台差异只在子类、
 * 平台在接线处（Vergvoke）二选一。相机 / 宇宙视图这类"两个平台行为一致"的输入仍留在各自模块里。
 *
 * <p>
 * {@link InputProcessor} 与 {@link GestureListener} 的方法都是 default，子类按需覆盖。
 */
public abstract class InputProcess implements InputProcessor, GestureListener {

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
