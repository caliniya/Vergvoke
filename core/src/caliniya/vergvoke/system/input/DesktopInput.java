package caliniya.vergvoke.system.input;

import arc.input.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.data.*;

/**
 * 桌面端输入：方向键/WASD 平移相机，滚轮缩放。
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
}
