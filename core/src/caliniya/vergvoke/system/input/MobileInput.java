package caliniya.vergvoke.system.input;

import arc.*;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.data.*;

/**
 * 移动端输入：先开"指挥"开关，再点单位选 / 点地图下令。
 *
 * <p>相机、宇宙视图、选中等平台无关逻辑都在基类 {@link InputProcess} 里，这里只覆盖触摸这一处平台差异。
 */
public class MobileInput extends InputProcess {

    @Override
    public boolean tap(float x, float y, int count, KeyCode button) {
        // 使用全局指挥状态判断
        if (!CommandData.commanding)
            return false;

        Vec2 worldPos = Core.camera.unproject(x, y);
        float wx = worldPos.x;
        float wy = worldPos.y;

        if (selectAt(wx, wy)) {
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
}
