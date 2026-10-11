package caliniya.vergvoke.system.input;

import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.data.*;

/**
 * 移动端输入：拖拽平移相机，双指捏合缩放。
 */
public class MobileInput extends Input {

	@Override
	public boolean pan(float x, float y, float deltaX, float deltaY) {
		if (blocked())
			return false;
		float s = CommandData.viewType == CommandData.ViewType.uni ? Render.universeZoom : Render.currentZoom;
		moveCamera(-deltaX * s, -deltaY * s);
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
