package caliniya.vergvoke.system.render;

import arc.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.base.type.*;

/**
 * 前处理渲染器：画在建筑之后、单位之前的叠加内容（框选指示、范围指示等）。
 * <p>要画的实现类往 {@link PreRender#renders} 注册即可，状态由各自模块维护。
 */
public class PreRender extends caliniya.vergvoke.system.System<PreRender> {

	/** 前处理绘制任务（每帧在建筑渲染后依次执行）。 */
	public static final arc.struct.Ar<Runnable> renders = new arc.struct.Ar<>();

	@Override
	public PreRender init() {
		this.index = 12;
		Events.run(EventType.events.EnterUV, () -> paused = true);
		Events.run(EventType.events.ExitUV, () -> paused = false);
		paused = true;
		return super.init(false, false);
	}

	@Override
	public void update(float delta) {
		for (Runnable r : renders) {
			r.run();
		}
		Draw.reset();
	}
}
