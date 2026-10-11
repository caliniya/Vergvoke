package caliniya.vergvoke.system.input;

import arc.input.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.ui.*;

/**
 * 移动端输入：拖拽平移相机，双指捏合缩放；
 * 指挥模式下长按进入框选（pan 期间框选），tap 点选单位。
 */
public class MobileInput extends Input {

	/** 指挥模式下游戏视图才响应选中类输入。 */
	private boolean canSelect() {
		return !blocked() && CommandData.commanding && CommandData.viewType == CommandData.ViewType.game && Game.inGame;
	}

	@Override
	public boolean touchDown(float x, float y, int pointer, KeyCode button) {
		// 按下即记框选起点：单指 pan 是相机平移，长按（longPress）后才升级为框选
		if (canSelect() && pointer == 0) {
			tranPos(x, y);
			boxX1 = worldX;
			boxY1 = worldY;
		}
		return super.touchDown(x, y, pointer, button);
	}

	/** 长按进入框选模式：pan 从相机平移切换为框选。 */
	@Override
	public boolean longPress(float x, float y) {
		if (canSelect()) {
			tranPos(x, y);
			boxX1 = worldX;
			boxY1 = worldY;
			boxDragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean pan(float x, float y, float deltaX, float deltaY) {
		if (blocked())
			return false;

		// 框选拖拽：只累计选择区域，不动相机（终点在 panStop 用当前触点结算）
		if (boxDragging) {
			tranPos(x, y);
			boxX2 = worldX;
			boxY2 = worldY;
			return true;
		}

		float s = CommandData.viewType == CommandData.ViewType.uni ? Render.universeZoom : Render.currentZoom;
		moveCamera(-deltaX * s, -deltaY * s);
		return false;
	}

	@Override
	public boolean panStop(float x, float y, int pointer, KeyCode button) {
		if (boxDragging) {
			tranPos(x, y);
			boxSelect(boxX1, boxY1, worldX, worldY);
			return true;
		}
		return false;
	}

	@Override
	public boolean tap(float x, float y, int count, KeyCode button) {
		if (!canSelect())
			return false;
		tranPos(x, y);
		// 四叉树按包围盒重叠判定，点选用小半径查询 + contains 精判
		Game.team.data().intersect(worldX, worldY, 1f, e -> e instanceof Unit, this::tapSelect);
		return false;
	}

	@Override
	public boolean zoom(float initialDistance, float distance) {
		if (blocked())
			return false;
		pinchZoom(initialDistance, distance);
		return true;
	}
}
