package caliniya.vergvoke.core;

import arc.*;
import arc.files.*;
import arc.math.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.io.*;
import caliniya.vergvoke.system.game.*;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.system.world.*;
import caliniya.vergvoke.world.stars.*;

public class Data {

	// 从指定文件加载整个地图，使用异步
	// 这一步仅加载所有数据，理论上讲 不应该影响任何游戏界面
	public static void load(Fi file) {
		load(file, null);
	}

	/** 加载整个地图，加载并进入游戏后执行 onEnter（主线程）。 */
	public static void load(Fi file, Runnable onEnter) {
		GameIO.load(file, d -> DataIO.load(d, onEnter));
	}

	// 这个方法会加载所有的系统
	// 所有渲染方法 在此阶段不应该启动
	public static void loadSystems() {
		Events.fire(EventType.events.ThreadedStop);
		Systems.systems.clear();

		GameProcess.it = new GameProcess();
		Render.it = new Render();

		// 渲染器并入 Render 的管线数组（不再注册为独立系统）
		Render.renders.clear();
		Render.renders.add(new MapRender());
		Render.renders.add(new BlockRender());
		Render.renders.add(new PreRender());
		Render.renders.add(new UnitRender());
		Render.renders.add(new PostRender());
		Render.renders.add(new UniverseRender());
		Render.renders.sort();

		BulletProcess.it = new BulletProcess();

		// 旧线程停透后重建瞬态容器（旧线程死前可能已把陈旧缓冲换进静态字段），并重刷四叉树范围
		WorldData.rebuildTransientContainers();
	}

	private static void register(caliniya.vergvoke.system.System<?> system) {
		if (system != null && !Systems.systems.contains(system)) {
			Systems.systems.add(system.init());
		}
	}

	// 初始化所有系统，允许其工作
	// 同时将游戏UI切换到游戏内部
	public static void enter() {
		register(GameProcess.it);
		Render.it.init();

		// 渲染器也统一初始化（并入 Render 后不再走上面的系统列表）
		for (caliniya.vergvoke.system.System<?> render : Render.renders) {
			render.init();
		}

		Systems.systems.sort();

		BulletProcess.it.init();

		// 非主线程的 @SystemDef 系统
		Systems.startThreads();

		Game.starMap = new StarMap(2000, 2000);

		Rand r = new Rand();

		for (int i = 0; i < 100; ++i) {
			StarNode A, B;
			A = new StarNode(r.random(0, 2000), r.random(0, 2000), "呃啊" + r.random(0, 2000));
			B = new StarNode(r.random(0, 2000), r.random(0, 2000), "呃啊" + r.random(0, 2000));
			Game.starMap.addNode(A);
			Game.starMap.addNode(B);
			Game.starMap.link(A, B);
		}

		// 标记进入游戏内：主循环开始驱动游戏更新（Game.update → 生成侧统一更新）
		Game.inGame = true;

		UI.Game();
	}
}
