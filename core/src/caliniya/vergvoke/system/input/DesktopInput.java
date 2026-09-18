package caliniya.vergvoke.system.input;

import arc.*;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;

/**
 * 桌面端输入：左键选中 / 右键执行当前指令 / 右键双击中断选中单位的操作。
 *
 * <p>
 * 只在游戏内（{@link Game#inGame}）生效；不依赖"指挥"开关。
 */
public class DesktopInput extends InputProcess {

    @Override
    public boolean tap(float x, float y, int count, KeyCode button) {
        if (!Game.inGame)
            return false;

        Vec2 worldPos = Core.camera.unproject(x, y);
        float wx = worldPos.x;
        float wy = worldPos.y;

        if (button == KeyCode.mouseLeft) {
            // 左键：点单位 = 选中 / 取消选中；点空地 = 清空选择
            if (selectAt(wx, wy)) {
                UI.hud.refreshCommand();
                return true;
            }

            if (!CommandData.checkedUnits.isEmpty()) {
                clearSelection();
                return true;
            }
            return false;
        }

        if (button == KeyCode.mouseRight) {
            // 右键双击：中断选中单位当前的操作
            if (count >= 2)
                return interruptSelected();

            // 右键：执行当前指令（移动 → 移到点击点；停止 → 原地停下）
            return executeSelected(wx, wy);
        }

        return false;
    }
}
