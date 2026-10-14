package caliniya.vergvoke.world.building;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.shaders.Shaders;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.ai.*;
import caliniya.vergvoke.type.type.*;

/**
 * 建造计划池：纯数据 + 静态门面（放置/取消/认领/副作用队列/预览全在这里）。
 *
 * <p>
 * 线程模型：主线程放置/取消、ai 线程认领/推进，共用 {@link #lock}；
 * 实体生命周期副作用（SPAWN/FINISH/DEMO）由 ai 线程 {@link #submit} 登记，
 * 主线程 {@link #drain} 锁内取快照、锁外执行（仿 {@code Entities.markDead} 范式）。
 */
public class BuildPlan {

	/// 计划池 + pend 队列共用锁
	public static final Object lock = new Object();

	/**
	 * 占位格缓冲（世界格坐标成对 x,y；仅主线程放置/预览/绘制路径用，消费完再给别人）。
	 *
	 * <p>drawFootprint / footprintsIntersect 每帧跑，lambda+临时数组的分配速率会把
	 * young gen 打满（表现为内存快速增长、minor GC 频繁），所以全部走静态缓冲纯循环。
	 */
	private static int[] tileBufA = new int[128]; // 64 格顶天，超大建筑罕见路径扩容
	private static int[] tileBufB = new int[128];

	/// 进行中的计划（插入序 = 优先级；ai 认领取从前向后第一个未认领的）
	public static final Ar<Plan> plans = new Ar<>();

	/// 副作用队列（ai 线程登记，主线程 drain）
	private static final Ar<Pend> pend = new Ar<>();

	/// 副作用操作码
	public static final byte SPAWN = 0, FINISH = 1, DEMO = 2;

	/** 单个计划：kind=0 建造 / 1 拆除，同池混列。 */
	public static class Plan {

		public byte kind;

		/// kind=0：要造的建筑
		public BuildingType block;

		/// kind=1：被拆的建筑
		public Building building;

		/// kind=0：锚点（左下角格）
		public int tx, ty, angle;

		/// 归属阵营（放置时记 Game.team；SPAWN 工地用）
		public TeamTypes team;

		/// 认领的主单位（null = 未认领）；volatile 供 ai 线程无锁读，主线程锁内写
		public volatile Unit main;

		/// kind=0：已 spawn 的工地（未进半径为 null）
		public Building site;

		/// kind=0：已登记 SPAWN 未执行（防重复登记）
		public boolean spawning;

		/// 出池标记（完工/取消后置 true；协助者靠它自检收尾）
		public boolean done;
	}

	/** 副作用条目（op + 目标计划）。 */
	public static class Pend {

		public byte op;
		public Plan plan;
	}

	// --- 主线程：放置 / 取消 ---

	/** 尝试放置建造计划（默认玩家队）：canPlace 通过且与本队未开工计划脚印不重叠才入池。 */
	public static boolean tryPlace(BuildingType block, int tx, int ty, int angle) {
		return tryPlace(block, tx, ty, angle, Game.team);
	}

	/**
	 * 尝试放置建造计划：canPlace 通过且与{@code team} 未开工计划脚印不重叠才入池。
	 *
	 * <p>计划池全局共享但按 {@link Plan#team} 隔离——敌人 AI 同样走本建造系统，
	 * 认领/重叠判定只认本队的，不会抢玩家的计划。
	 *
	 * @return true = 已入池
	 */
	public static boolean tryPlace(BuildingType block, int tx, int ty, int angle, TeamTypes team) {
		if (block == null || !block.canPlace(tx, ty, angle)) {
			return false;
		}
		synchronized (lock) {
			// 与未开工计划脚印重叠也拒（错开锚点/换建筑类型都不行——spawn 时会互相覆盖拆除；
			// 已 spawn 的脚印已被工地占了，canPlace 自然挡住）
			if (overlapsPlans(block, tx, ty, angle, team)) {
				return false;
			}
			Plan p = new Plan();
			p.kind = 0;
			p.block = block;
			p.tx = tx;
			p.ty = ty;
			p.angle = angle % 4;
			p.team = team;
			plans.add(p);
			return true;
		}
	}

	/**
	 * 与{@code team} 未开工计划的脚印是否重叠（主线程放置/预览用）。
	 *
	 * <p>同锚点只是重叠的一种；错开锚点但脚印相交（2×2 放 (5,5) 与 (6,6)）同样要拒。
	 */
	public static boolean overlapsPlans(BuildingType block, int tx, int ty, int angle, TeamTypes team) {
		synchronized (lock) {
			for (Plan q : plans) {
				if (q.kind != 0 || q.site != null || q.block == null || q.team != team) {
					continue;
				}
				if (footprintsIntersect(block, tx, ty, angle, q.block, q.tx, q.ty, q.angle)) {
					return true;
				}
			}
			return false;
		}
	}

	private static boolean footprintsIntersect(
			BuildingType a, int ax, int ay, int aAngle, BuildingType b, int bx, int by, int bAngle) {
		// 计划数量小（玩家手放几十个顶天），逐格相交，不做粗筛；走静态缓冲，每帧零分配
		int na = fillTiles(a, ax, ay, aAngle, true);
		int nb = fillTiles(b, bx, by, bAngle, false);
		for (int i = 0; i < na; i++) {
			int x1 = tileBufA[i * 2], y1 = tileBufA[i * 2 + 1];
			for (int j = 0; j < nb; j++) {
				if (x1 == tileBufB[j * 2] && y1 == tileBufB[j * 2 + 1]) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 把占位格（世界格坐标，成对）填进静态缓冲，返回格数。
	 *
	 * @param useA true = tileBufA，false = tileBufB（两份并存用于相交检测）
	 */
	private static int fillTiles(BuildingType block, int tx, int ty, int angle, boolean useA) {
		int[] buf = useA ? tileBufA : tileBufB;
		int max = block.shapeOffsets != null ? block.shapeOffsets.length : block.size * block.size * 2;
		if (buf.length < max) {
			buf = new int[max]; // 超大建筑（>8×8）罕见，扩一次
			if (useA) {
				tileBufA = buf;
			} else {
				tileBufB = buf;
			}
		}
		int n = 0;
		if (block.shapeOffsets != null) {
			// 异形每帧一次 getRotatedOffsets 分配（小头，当前无异形建筑内容；
			// 真要抠，在 BuildingType.load 里按角度缓存旋转结果）
			int[] offs = BuildingType.getRotatedOffsets(angle % 4, block.shapeOffsets);
			for (int i = 0; i < offs.length; i += 2) {
				buf[n++] = tx + offs[i];
				buf[n++] = ty + offs[i + 1];
			}
		} else {
			for (int dx = 0; dx < block.size; dx++) {
				for (int dy = 0; dy < block.size; dy++) {
					buf[n++] = tx + dx;
					buf[n++] = ty + dy;
				}
			}
		}
		return n / 2;
	}

	private static boolean containsBuf(int[] buf, int n, int qx, int qy) {
		for (int i = 0; i < n; i++) {
			if (buf[i * 2] == qx && buf[i * 2 + 1] == qy) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 画占位区外轮廓：逐格查四邻，只画不与本方案其他格共享的边。
	 *
	 * <p>方形建筑 = 一个整框；异形 = 贴合的真实边界（不是每格一框）。对角相邻的格会留尖角，
	 * 恰好表明对角不连通。走静态缓冲纯循环，每帧零分配。
	 */
	private static void drawFootprint(BuildingType block, int tx, int ty, int angle) {
		float ts = WorldData.TILE_SIZE;
		int n = fillTiles(block, tx, ty, angle, true);
		for (int i = 0; i < n; i++) {
			int x = tileBufA[i * 2], y = tileBufA[i * 2 + 1];
			if (!containsBuf(tileBufA, n, x - 1, y)) {
				Lines.line(x * ts, y * ts, x * ts, (y + 1) * ts);
			}
			if (!containsBuf(tileBufA, n, x + 1, y)) {
				Lines.line((x + 1) * ts, y * ts, (x + 1) * ts, (y + 1) * ts);
			}
			if (!containsBuf(tileBufA, n, x, y - 1)) {
				Lines.line(x * ts, y * ts, (x + 1) * ts, y * ts);
			}
			if (!containsBuf(tileBufA, n, x, y + 1)) {
				Lines.line(x * ts, (y + 1) * ts, (x + 1) * ts, (y + 1) * ts);
			}
		}
	}

	/**
	 * 取消计划：出池 + 锁内解绑认领。
	 *
	 * <p>认领单位的 cur 不清（跨线程写 ai 字段不安全）——由 BuildAi 每帧自检
	 * {@code cur.main != u} 后自行置空。
	 */
	public static void cancel(Plan p) {
		if (p == null) {
			return;
		}
		synchronized (lock) {
			plans.remove(p, true);
			p.main = null;
			p.done = true; // 出池标记：持有引用的协助者/命令单位靠它自检收尾
		}
	}

	// --- 跨线程：认领 / 解绑 ---

	/** 解绑认领（单位死亡 / 收到新指令时主线程调；AI 侧靠自检收尾）。 */
	public static void release(Plan p, Unit u) {
		if (p == null) {
			return;
		}
		synchronized (lock) {
			if (p.main == u) {
				p.main = null;
			}
		}
	}

	/// 标记拆除（普通模式右键点建筑）：入池 kind=1 计划；同一建筑只保留一条
	public static void addDemolish(Building b) {
		addDemolish(b, Game.team);
	}

	/// 标记拆除（带队伍）：拆除计划归下令人的队伍（claimFirst 按队隔离）
	public static void addDemolish(Building b, TeamTypes team) {
		if (b == null || b.type == null) {
			return;
		}
		synchronized (lock) {
			for (Plan p : plans) {
				if (p.kind == 1 && p.building == b) {
					return; // 已标记过
				}
			}
			startDemolish(b, team);
		}
	}

	/**
	 * 原子认领（ai 线程）：锁内找第一个本队未认领的计划并绑给 u。
	 *
	 * <p>拆除优先于建造（kind=1 先扫）——标记了拆除就该尽快响应；同 kind 内按入池序
	 * （先来先建）。按 {@code u.team} 过滤——敌人认领不到玩家的计划。
	 */
	public static Plan claimFirst(Unit u) {
		synchronized (lock) {
			for (int kind = 1; kind >= 0; kind--) {
				for (Plan p : plans) {
					if (p.kind == kind && p.team == u.team && p.main == null && !p.done) {
						p.main = u;
						return p;
					}
				}
			}
			return null;
		}
	}

	/** 解绑单位的一切认领（主线程：单位死亡 / 玩家下达移动指令）。 */
	public static void releaseUnit(Unit u) {
		synchronized (lock) {
			for (Plan p : plans) {
				if (p.main == u) {
					p.main = null;
				}
			}
		}
	}

	/// 读档恢复建造计划（直接入池，不过 canPlace/防重——存的就是当时合法的位置）
	public static void restorePlan(BuildingType block, int tx, int ty, int angle, TeamTypes team) {
		Plan p = new Plan();
		p.kind = 0;
		p.block = block;
		p.tx = tx;
		p.ty = ty;
		p.angle = angle % 4;
		p.team = team;
		synchronized (lock) {
			plans.add(p);
		}
	}

	/// 尝试认领指定计划（玩家下令用）：已被认领则失败（调用方按协助处理）
	public static boolean claim(Plan p, Unit u) {
		if (p == null || u == null) {
			return false;
		}
		synchronized (lock) {
			if (p.main == null) {
				p.main = u;
				return true;
			}
			return false;
		}
	}

	/// 光标世界坐标落在哪个未 spawn 的计划脚印上（指挥下令用；主线程）
	public static Plan planAt(float wx, float wy) {
		int tx = (int) (wx / WorldData.TILE_SIZE), ty = (int) (wy / WorldData.TILE_SIZE);
		synchronized (lock) {
			for (Plan p : plans) {
				if (p.kind != 0 || p.block == null || p.site != null) {
					continue;
				}
				int n = fillTiles(p.block, p.tx, p.ty, p.angle, true);
				if (containsBuf(tileBufA, n, tx, ty)) {
					return p;
				}
			}
			return null;
		}
	}

	/// 反查工地对应的计划（指挥协助令用；主线程）
	public static Plan planOfSite(Building b) {
		if (b == null) {
			return null;
		}
		synchronized (lock) {
			for (Plan p : plans) {
				if (p.site == b) {
					return p;
				}
			}
			return null;
		}
	}

	/**
	 * 把无计划的在建工地纳入建造（指挥下令用；主线程）。
	 *
	 * <p>取消拆除后半成品工地（progress 被扣一半、没人建）的恢复入口；
	 * 已有计划的原地返回。工地直接以 {@code site} 入池，不走 spawn 流程。
	 */
	public static Plan adoptSite(Building b) {
		if (b == null || b.type == null || b.finished) {
			return null;
		}
		synchronized (lock) {
			for (Plan p : plans) {
				if (p.site == b) {
					return p;
				}
			}
			Plan p = new Plan();
			p.kind = 0;
			p.block = b.type;
			p.tx = b.tx;
			p.ty = b.ty;
			p.angle = b.angle;
			p.team = b.team;
			p.site = b;
			plans.add(p);
			return p;
		}
	}

	/// 反查建筑的拆除计划（toggle 用；主线程）
	public static Plan demolishPlanOf(Building b) {
		if (b == null) {
			return null;
		}
		synchronized (lock) {
			for (Plan p : plans) {
				if (p.kind == 1 && p.building == b) {
					return p;
				}
			}
			return null;
		}
	}

	/**
	 * 标记拆除 / 取消拆除（普通模式右键建筑；toggle）。
	 *
	 * <p>拆除与建造同池混列（kind=1），走同一套认领/前往/推进，只是方向相反：
	 * 持续返还物品（×{@code Game.deconstructRefund}）而非消耗。目标若是在造的工地，
	 * 先取消它的建造计划——直接开拆，不等造完。
	 *
	 * @return true = 已标记拆除；false = 取消了已有标记
	 */
	public static boolean toggleDemolish(Building b) {
		if (b == null || b.type == null) {
			return false;
		}
		synchronized (lock) {
			Plan exist = demolishPlanOf(b);
			if (exist != null) {
				cancel(exist);
				return false;
			}
			startDemolish(b, b.team);
			return true;
		}
	}

	/**
	 * 入池拆除计划，并把在建工地的原建造单位直接转派过来（调用方持锁）。
	 *
	 * <p>不等认领队列：单位刚从造它被叫停，立刻转岗去拆，视觉上立马开始拆。
	 * 转派 = 占主位（{@code d.main}）+ 派指定目标（{@code ai.orderedPlan}，主线程写、
	 * ai 线程下帧自检 {@code cur.main} 失效后接管）。
	 */
	private static Plan startDemolish(Building b, TeamTypes team) {
		Plan d = new Plan();
		d.kind = 1;
		d.building = b;
		d.team = team;
		plans.add(d);

		// 在造工地：取消建造计划（工地与进度保留，只停建），原建造单位转派来拆
		for (Plan p : plans) {
			if (p.kind == 0 && p.site == b) {
				Unit u = p.main;
				cancel(p);
				if (u != null && u.health > 0f && u.ai instanceof BuildAi ai) {
					d.main = u;
					ai.orderedPlan = d;
					u.ai.ordered = true; // 玩家介入：同 moveUsers 语义，之后不自动认领
				}
				break; // 一个工地一条建造计划
			}
		}
		return d;
	}

	// --- 副作用队列 ---

	/** 登记副作用（ai 线程；只入队不执行）。 */
	public static void submit(byte op, Plan plan) {
		if (plan == null) {
			return;
		}
		synchronized (lock) {
			Pend e = new Pend();
			e.op = op;
			e.plan = plan;
			pend.add(e);
		}
	}

	/** 取副作用快照（主线程；锁内拷贝、锁外执行，避免持 plans 锁去拿 EntityArs 写锁）。 */
	public static void drain(Ar<Pend> out) {
		out.clear();
		synchronized (lock) {
			out.addAll(pend);
			pend.clear();
		}
	}

	// --- 渲染（挂 PostRender.renders，DesktopInput 构造器注册） ---

	/**
	 * 建造预览：光标左下角格处画占位区外轮廓 + 贴图虚影。
	 * 可建 = 半透明白 + 天蓝轮廓；不可建 = 红色 tint（越界/已有建筑/固体 ENVBlock/计划重叠）。
	 *
	 * <p>锚点语义：{@code Draw.rect} 中心锚（贴图用），{@code Lines} 线段用世界像素坐标。
	 */
	public static void drawPreview(float wx, float wy) {
		if (CommandData.mode != CommandData.Mode.build || CommandData.buildBlock == null
				|| CommandData.viewType != CommandData.ViewType.game) {
			return;
		}
		BuildingType block = CommandData.buildBlock;
		int tx = (int) (wx / WorldData.TILE_SIZE), ty = (int) (wy / WorldData.TILE_SIZE);
		int angle = CommandData.planAngle;
		boolean ok = block.canPlace(tx, ty, angle) && !overlapsPlans(block, tx, ty, angle, Game.team);
		float ts = WorldData.TILE_SIZE;

		// 占位区外轮廓（一笔勾出整体边界，不再是每格一个框）
		Draw.color(ok ? Color.sky : Color.scarlet, ok ? 0.9f : 0.7f);
		Lines.stroke(1.5f);
		drawFootprint(block, tx, ty, angle);

		// 贴图虚影（锚点=左下角格，绕脚印中心旋转）
		Draw.color(ok ? Color.white : Color.scarlet, ok ? 0.45f : 0.3f);
		float psize = block.psize();
		Draw.rect(block.region, tx * ts + psize / 2f, ty * ts + psize / 2f, psize, psize, angle * 90f);
		Draw.color();
	}

	/**
	 * 计划池可视化（挂 PostRender，每帧）：未 spawn 画脚印虚影，工地走 blockbuild
	 * shader（中心擦除+前沿亮边+轮廓扫描，建造青/拆除红），认领单位 ↔ 目标画连线。
	 * 锁内直画（临界区只有字段读；progress 是 volatile）。
	 *
	 * <p>多队伍：目前全队都画（敌人计划也可见）；等战争迷雾/视线系统就位后按队伍过滤。
	 */
	public static void drawLinksAndFx() {
		synchronized (lock) {
			float ts = WorldData.TILE_SIZE;
			for (Plan p : plans) {
				float cx, cy;

				if (p.kind == 1) {
					// 拆除计划：建筑红叉标记（建筑没了的计划自清，不画）
					Building b = p.building;
					if (b == null || b.type == null) {
						continue;
					}
					cx = b.x;
					cy = b.y;
					if (b.finished) {
						// 还没开拆（成品）：红叉标记
						Draw.color(Color.scarlet, 0.3f);
						Draw.rect(b.type.region, b.x, b.y, b.angle * 90f);
						Draw.color(Color.scarlet, 0.9f);
						Lines.stroke(2f);
						float h = b.size / 2f;
						Lines.line(b.x - h, b.y - h, b.x + h, b.y + h);
						Lines.line(b.x - h, b.y + h, b.x + h, b.y - h);
						Draw.color();
					} else {
						// 拆除中（已降级）：红色 shader 倒扣视觉，progress 越低遮挡越多
						drawSite(b, b.buildTime > 0f ? b.progress / b.buildTime : 0f, Color.scarlet);
					}
				} else {
					if (p.block == null) {
						continue;
					}
					float psize = p.block.psize();
					cx = (p.tx + p.block.size / 2f) * ts;
					cy = (p.ty + p.block.size / 2f) * ts;

					Building site = p.site;
					if (site == null || site.type == null) {
						// 未 spawn（或工地已毁）：脚印虚影 + 整体轮廓
						Draw.color(Color.white, 0.25f);
						Draw.rect(p.block.region, cx, cy, psize, psize, p.angle * 90f);
						Draw.color(Color.sky, 0.5f);
						Lines.stroke(1f);
						drawFootprint(p.block, p.tx, p.ty, p.angle);
						Draw.color();
					} else {
						// 工地：blockbuild shader（中心擦除+前沿亮边+轮廓扫描），建造青
						float prog = site.buildTime > 0f ? site.progress / site.buildTime : 1f;
						drawSite(site, prog, Pal.light);
						// 前沿抖火花（普通批次，活跃反馈）
						Draw.color(Color.orange, 0.7f);
						for (int i = 0; i < 2; i++) {
							Fill.rect(site.x + Mathf.random(psize) - psize / 2f,
									site.y + Mathf.random(psize) - psize / 2f, 2f, 2f);
						}
						Draw.color();
					}
				}

				// 认领单位 ↔ 目标连线（拆除=红线）
				Unit main = p.main;
				if (main != null && main.type != null && main.health > 0f) {
					Draw.color(p.kind == 1 ? Color.scarlet : Pal.light, 0.7f);
					Lines.stroke(1.5f);
					Lines.line(main.x, main.y, cx, cy);
					Draw.color();
				}
			}

			// 无计划的半成品工地（拆一半取消留下的）：画进度条，提示它还能被下令续建
			EntityArs.Building.each(b -> {
				if (b == null || b.type == null || b.finished) {
					return;
				}
				boolean tracked = false;
				for (Plan q : plans) {
					if (q.site == b || q.building == b) {
						tracked = true;
						break;
					}
				}
				if (tracked) {
					return;
				}
				drawSite(b, b.buildTime > 0f ? b.progress / b.buildTime : 1f, Pal.light);
			});
		}
	}

	/// 工地 shader 绘制（blockbuild：中心擦除+前沿亮边+轮廓扫描；tint=亮边/扫描线颜色）
	private static void drawSite(Building b, float prog, Color tint) {
		Shaders.blockbuild.progress = prog;
		Shaders.blockbuild.time = Time.time;
		Shaders.blockbuild.alpha = 1f;
		Draw.color(tint);
		Draw.shader(Shaders.blockbuild);
		Draw.rect(b.type.region, b.x, b.y, b.size, b.size, b.angle * 90f);
		Draw.flush();
		Draw.shader();
		Draw.color();
	}
}
