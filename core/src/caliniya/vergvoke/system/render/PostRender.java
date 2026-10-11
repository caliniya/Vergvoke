package caliniya.vergvoke.system.render;

import arc.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.base.type.*;

/**
 * 后处理渲染器：画在所有世界内容之后的顶层叠加内容（指令线、预览轮廓等）。
 * <p>要画的实现类往 {@link PostRender#renders} 注册即可，状态由各自模块维护。
 */
public class PostRender extends caliniya.vergvoke.system.System<PostRender> {

	/** 后处理绘制任务（每帧在所有世界渲染后依次执行）。 */
	public static final arc.struct.Ar<Runnable> renders = new arc.struct.Ar<>();

	@Override
	public PostRender init() {
		this.index = 20;
		// 游戏视图叠加层：进宇宙暂停，退出恢复
		Events.run(EventType.events.EnterUV, () -> paused = true);
		Events.run(EventType.events.ExitUV, () -> paused = false);
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
