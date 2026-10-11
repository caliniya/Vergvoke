package caliniya.vergvoke.system.input;

import arc.*;
import arc.input.GestureDetector.*;
import arc.input.*;
import arc.math.geom.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.ui.windows.*;

/**
 * 输入基类：实现平台无关的具体操作（移动相机 / 缩放等），平台子类在各手势、按键事件中接入这些操作。
 * <p>视图分流统一走 {@link CommandData#viewType}；暂停（GamePause）或界面存在模态窗口时（{@link Window#hasModal()}）不响应相机类操作。
 */
public abstract class Input implements InputProcessor, GestureListener {

	/// 当前指针/手指所在的世界坐标
	public float worldX, worldY;

	/// 游戏暂停标志（暂停时不响应相机类输入）
	protected boolean paused;

	public Input() {
		Events.on(EventType.GamePause.class, e -> paused = e.pause);
	}

	/** 是否应屏蔽背景输入：暂停中或存在可见的模态窗口。 */
	protected boolean blocked() {
		return paused || Window.hasModal();
	}

	/** 每帧由主循环驱动（键盘平移等平台自有逻辑在子类覆盖）。 */
	public void update(float delta) {

	}

	public void tranPos(float x, float y) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			Vec2 temp = Core.camera.unproject(x, y);
			worldX = temp.x;
			worldY = temp.y;
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Vec2 temp = Render.uniCamera.unproject(x, y);
			worldX = temp.x;
			worldY = temp.y;
		}
	}

	/** 按当前视图平移对应相机。 */
	public void moveCamera(float x, float y) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			Core.camera.position.add(x, y);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Render.uniCamera.position.add(x, y);
		}
	}

	/** 滚轮缩放：按当前视图调 Render 的缩放（内部自带 clamp）。 */
	public void wheelZoom(float amountY) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			Render.zoom(amountY * 0.1f * Render.currentZoom);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Render.zoomUniverse(amountY * 0.1f * Render.universeZoom);
		}
	}

	/** 捏合缩放（移动端双指）：按两指距离比例缩放当前视图相机。 */
	public void pinchZoom(float initialDistance, float distance) {
		if (initialDistance == 0)
			return;
		float ratio = initialDistance / distance;
		if (CommandData.viewType == CommandData.ViewType.game) {
			Render.setZoom(pinchSnapshot * ratio);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Render.setUniverseZoom(pinchSnapshot * ratio);
		}
	}

	/// 捏合缩放开始前的缩放快照（touchDown 时记录）
	protected float pinchSnapshot = 1f;

	/** 按下时记录当前缩放，作为捏合缩放基准。 */
	@Override
	public boolean touchDown(float x, float y, int pointer, KeyCode button) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			pinchSnapshot = Render.currentZoom;
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			pinchSnapshot = Render.universeZoom;
		}
		return false;
	}
}
