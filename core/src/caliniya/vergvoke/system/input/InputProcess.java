package caliniya.vergvoke.system.input;

import arc.input.GestureDetector.*;
import arc.input.*;

/**
 * 输入处理基类（待重构：原相机平移/缩放、选中、下令逻辑已全部清除）。
 * <p>平台差异由 {@link DesktopInput} / {@link MobileInput} 实现。
 */
public abstract class InputProcess implements InputProcessor, GestureListener {

	/// 当前指针/手指所在的世界坐标
	public float worldX, worldY;

	/** 每帧由主循环驱动。 */
	public void update(float delta) {

	}

	public void tranPos(float x, float y) {

	}

}
