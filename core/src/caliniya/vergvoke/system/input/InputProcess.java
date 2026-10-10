package caliniya.vergvoke.system.input;

import arc.input.GestureDetector.*;
import arc.input.*;

/**
 * 输入处理基类（待重构：原相机平移/缩放、选中、下令逻辑已全部清除）。
 * <p>平台差异由 {@link DesktopInput} / {@link MobileInput} 实现。
 */
public abstract class InputProcess implements InputProcessor, GestureListener {

	/** 每帧由主循环驱动。 */
	public void update(float delta) {
	}
}
