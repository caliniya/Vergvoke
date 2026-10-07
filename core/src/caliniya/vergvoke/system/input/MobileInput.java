package caliniya.vergvoke.system.input;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;

/**
 * 移动端输入：先开"指挥"开关，再点单位选 / 点地图下令。
 *
 * <p>相机、宇宙视图、选中等平台无关逻辑都在基类 {@link InputProcess} 里，这里只覆盖触摸这一处平台差异。
 */
public class MobileInput extends InputProcess {

	@Override
	public boolean tap(float x, float y, int count, KeyCode button) {
		// 宇宙视图优先分流：点按拾取星域节点
		if (inUniverse) {
			return pickNodeAt(x, y);
		}

		// 游戏内才有世界坐标可言
		if (!Game.inGame)
			return false;

		Vec2 worldPos = Core.camera.unproject(x, y);
		float wx = worldPos.x;
		float wy = worldPos.y;

		// 点建筑 = 信息面板：不依赖指挥开关，任何时候都能看
		if (openBuildingAt(wx, wy)) {
			return true;
		}

		// 使用全局指挥状态判断
		if (!CommandData.commanding)
			return false;

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
