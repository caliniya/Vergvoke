package caliniya.vergvoke.system.input;

import arc.input.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.ui.*;

/**
 * 桌面端输入：方向键/WASD 平移相机，滚轮缩放；
 * 指挥模式下左键点选单位，左键拖拽框选（tap 与 pan 由 GestureDetector 区分）。
 */
public class DesktopInput extends Input {

	/** 键盘平移速度（像素/帧，随当前缩放放大）。 */
	private static final float keySpeed = 10f;

	/// 方向键/WASD 按住标志
	private boolean up, down, left, right;

	@Override
	public void update(float delta) {
		if (blocked())
			return;

		// 帧时间乘进来（60TPS 时 delta ≈ 1），随缩放放大保证手感一致
		float speed = keySpeed * delta * (CommandData.viewType == CommandData.ViewType.uni ? Render.universeZoom : Render.currentZoom);

		if (up)
			moveCamera(0, speed);
		if (down)
			moveCamera(0, -speed);
		if (left)
			moveCamera(-speed, 0);
		if (right)
			moveCamera(speed, 0);
	}

	@Override
	public boolean keyDown(KeyCode key) {
		if (blocked())
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

	@Override
	public boolean scrolled(float amountX, float amountY) {
		if (blocked())
			return false;
		wheelZoom(amountY);
		return true;
	}

	/** 指挥模式下游戏视图才响应选中类输入。 */
	private boolean canSelect() {
		return !blocked() && CommandData.commanding && CommandData.viewType == CommandData.ViewType.game && Game.inGame;
	}

	@Override
	public boolean touchDown(float x, float y, int pointer, KeyCode button) {
		// 左键按下且可选中：记框选起点（是否真的成框由 GestureDetector 区分 tap/pan 决定）
		if (canSelect() && button == KeyCode.mouseLeft) {
			tranPos(x, y);
			boxX1 = worldX;
			boxY1 = worldY;
			selStartX = x;
			selStartY = y;
			selEndX = x;
			selEndY = y;
			boxDragging = false;
		}
		return super.touchDown(x, y, pointer, button);
	}

	@Override
	public boolean tap(float x, float y, int count, KeyCode button) {
		if (!canSelect())
			return false;
		tranPos(x, y);

		// 右键 = 按当前指挥状态下发指令
		if (button == KeyCode.mouseRight) {
			issueCommand(worldX, worldY);
			return true;
		}

		// 左键 = 点选：四叉树按包围盒重叠判定，小半径查询 + contains 精判
		if (button == KeyCode.mouseLeft) {
			Game.team.data().intersect(worldX, worldY, 1f, e -> e instanceof Unit, this::tapSelect);
			return false;
		}
		return false;
	}

	@Override
	public boolean pan(float x, float y, float deltaX, float deltaY) {
		// 左键拖拽 = 框选中：只记屏幕终点，相机不平移（终点世界坐标绘制/结算时反投影）
		if (canSelect()) {
			selEndX = x;
			selEndY = y;
			boxDragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean panStop(float x, float y, int pointer, KeyCode button) {
		// 拖拽结束：结算框选（终点实时反投影）
		if (canSelect() && boxDragging) {
			selEndX = x;
			selEndY = y;
			boxSelectEnd();
			return true;
		}
		return false;
	}
}
