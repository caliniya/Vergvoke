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
import java.util.function.Supplier;

/**
 * 调试测试窗：HUD 的「调试」小按钮开关，集中放各种测试入口。
 *
 * <p>新增测试一行注册：{@code DebugWindow.register("名字", () -> ...)}，
 * 自动出现在窗口列表里；动作里缺世界/系统的前置条件自己兜（判空 + Log 提示）。
 *
 * <p>标题可传 {@code Supplier<String>} 做成动态的——按钮每次点击后重取文案，
 * 开关类测试项的文字就能跟着状态变化。
 */
public class DebugWindow extends Window {

	/** 已注册的测试项（保持插入顺序）。 */
	private static final List<Entry> entries = new ArrayList<>();

	private static DebugWindow current;

	/** 一个测试项：label 每次点击后重取，静态文案包一层 lambda 即可。 */
	private record Entry(Supplier<String> label, Runnable run) {
		Entry(String label, Runnable run) {
			this(() -> label, run);
		}
	}

	/** 注册一个固定标题的测试项。 */
	public static void register(String name, Runnable run) {
		register(() -> name, run);
	}

	/** 注册一个动态标题的测试项（label 在窗口构建时与每次点击后重取）。 */
	public static void register(Supplier<String> label, Runnable run) {
		entries.add(new Entry(label, run));
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

		register(
				"生成工厂(镜头中心)",
				() -> {
					if (WorldData.world == null) {
						Log.info("[调试] 世界未初始化");
						return;
					}
					int tx = (int) (Core.camera.position.x / WorldData.TILE_SIZE);
					int ty = (int) (Core.camera.position.y / WorldData.TILE_SIZE);
					WorldData.placeBuilding(Blocks.factory, tx, ty, 0, Game.team);
				});

		register(
				"打印建筑库存(镜头中心)",
				() -> {
					if (WorldData.world == null) {
						Log.info("[调试] 世界未初始化");
						return;
					}
					int tx = (int) (Core.camera.position.x / WorldData.TILE_SIZE);
					int ty = (int) (Core.camera.position.y / WorldData.TILE_SIZE);
					caliniya.vergvoke.base.ecs.Building b = WorldData.world.getBuilding(tx, ty);
					if (b == null || b.item == null) {
						Log.info("[调试] 该处没有建筑（或无仓库）");
						return;
					}
					caliniya.vergvoke.type.def.craft.recipe.Recipe r =
							b.stack == null ? null : b.stack.current();
					Log.info(
							"[库存] Ge=@/@, 进度=@/@, 配方=@/@",
							b.item.getAmount(Items.Ge),
							b.item.capacity,
							b.stack == null ? 0f : b.stack.progress,
							r == null ? 0f : r.time,
							b.stack == null ? -1 : b.stack.current,
							b.stack == null ? 0 : b.stack.recipes.size);
				});

		register(
				"切换工厂配方(镜头中心)",
				() -> {
					if (WorldData.world == null) {
						Log.info("[调试] 世界未初始化");
						return;
					}
					int tx = (int) (Core.camera.position.x / WorldData.TILE_SIZE);
					int ty = (int) (Core.camera.position.y / WorldData.TILE_SIZE);
					caliniya.vergvoke.base.ecs.Building b = WorldData.world.getBuilding(tx, ty);
					if (b == null || b.stack == null || b.stack.recipes.isEmpty()) {
						Log.info("[调试] 该处没有带配方的建筑");
						return;
					}
					int next = (b.stack.current + 1) % b.stack.recipes.size;
					b.stack.select(next);
					Log.info("[配方] 切到 @/@（index=@）", next + 1, b.stack.recipes.size, next);
				});

		register(
				() -> DebugRender.it != null && DebugRender.it.enabled
						? "停用调试渲染器"
						: "启用调试渲染器",
				() -> {
					if (DebugRender.it == null) {
						Log.info("[调试] 渲染器未创建");
						return;
					}
					DebugRender.it.setEnable(!DebugRender.it.enabled);
				});

		register(
				() -> DebugRender.it != null && DebugRender.it.drawUI ? "关闭UI边框" : "启动UI边框",
				() -> {
					if (DebugRender.it == null) {
						Log.info("[调试] 渲染器未创建");
						return;
					}
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
		for (Entry e : entries) {
			// holder 中转：lambda 要在构造参数里引用按钮自身，直接写 b 过不了 definite assignment
			Button[] holder = new Button[1];
			holder[0] = new Button(e.label().get(), () -> {
				e.run().run();
				// 点击后重取标题：开关类测试项的文字跟着状态变化
				holder[0].text.setText(e.label().get());
			});
			t.add(holder[0]).growX().pad(2f);
			t.row();
		}
	}
}
