package caliniya.vergvoke.ui.windows;

import arc.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.ui.*;

import java.util.*;

/**
 * 调试测试窗：HUD 的「调试」小按钮开关，集中放各种测试入口。
 *
 * <p>新增测试一行注册：{@code DebugWindow.register("名字", () -> ...)}，
 * 自动出现在窗口列表里；动作里缺世界/系统的前置条件自己兜（判空 + Log 提示）。
 */
public class DebugWindow extends Window {

	/** 已注册的测试项（保持插入顺序，同名覆盖）。 */
	private static final Map<String, Runnable> entries = new LinkedHashMap<>();

	private static DebugWindow current;

	/** 注册一个测试项。 */
	public static void register(String name, Runnable run) {
		entries.put(name, run);
	}

	/** 调试按钮回调：开关窗口。 */
	public static void toggle() {
		if (current != null) {
			current.remove();
			current = null;
		} else {
			current = new DebugWindow();
			current.build();
		}
	}

	static {
		// ===== 种子测试项（示例，随用随改随删）=====

		register(
				"生成敌方炮塔(地图中心)",
				() -> {
					if (WorldData.world == null) {
						Log.info("[调试] 世界未初始化");
						return;
					}
					WorldData.placeBuilding(
							Blocks.testTurret,
							WorldData.world.W / 2,
							WorldData.world.H / 2,
							0,
							TeamTypes.Mutex);
				});

		register(
				"生成玩家单位(镜头中心)",
				() -> {
					if (WorldData.world == null) {
						Log.info("[调试] 世界未初始化");
						return;
					}
					UnitTypes.test.create(Game.team, Core.camera.position.x, Core.camera.position.y);
				});

		register(
				"清空全部单位",
				() -> {
					EntityArs.Unit.clear(unit -> unit.reset());
				});

		register(
				"打印导航统计",
				() -> {
					if (RouteData.layers == null) {
						Log.info("[导航] 未初始化");
						return;
					}
					for (var layer : RouteData.layers) {
						int used = 0, edges = 0, crossings = 0;
						for (RouteData.ChunkNav nav : layer.chunkNav) {
							if (nav == null) continue;
							used++;
							edges += nav.edges.size;
							crossings += nav.crossings.size;
						}
						Log.info(
								"[导航] chunk已建=@/边=@/门口=@/entrances=@",
								used,
								edges,
								crossings,
								layer.entrances.size);
					}
				});

		register(
				"重新载入存档(space.aevs)",
				() -> {
					Data.load(Core.settings.getDataDirectory().child("map/space.aevs"), null);
				});

		register(DebugRender.it.drawUI ? "关闭UI绘制" : "启动UI绘制", () -> {
			DebugRender.it.drawUI = !DebugRender.it.drawUI;
		});
	}

	public DebugWindow() {
		super("调试");
		w = 300f;
		h = 240f;
		showFullButton = false;
	}

	@Override
	public void main(Table t) {
		for (Map.Entry<String, Runnable> e : entries.entrySet()) {
			t.add(new Button(e.getKey(), e.getValue())).growX().pad(2f);
			t.row();
		}
	}
}
