package caliniya.vergvoke.system.input;

import arc.*;
import arc.input.GestureDetector.*;
import arc.input.*;
import arc.math.geom.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.data.*;

/**
 * 输入处理基类
 * <p>平台差异由 {@link DesktopInput} / {@link MobileInput} 实现。
 */
public abstract class Input implements InputProcessor, GestureListener {

	/// 当前指针/手指所在的世界坐标
	public float worldX, worldY;

	/** 每帧由主循环驱动。 */
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

	public void moveCamera(float x, float y) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			Core.camera.position.add(x, y);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Render.uniCamera.position.add(x, y);
		}
	}


}
