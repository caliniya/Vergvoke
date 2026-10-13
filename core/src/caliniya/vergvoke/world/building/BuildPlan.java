package caliniya.vergvoke.world.building;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.game.data.*;
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

		/// 认领的主单位（null = 未认领）；volatile 供 ai 线程无锁读，主线程锁内写
		public volatile Unit main;

		/// kind=0：已 spawn 的工地（未进半径为 null）
		public Building site;

		/// kind=1：拆除进度 0..1
		public float progress;

		/// kind=0：已登记 SPAWN 未执行（防重复登记）
		public boolean spawning;
	}

	/** 副作用条目（op + 目标计划）。 */
	public static class Pend {

		public byte op;
		public Plan plan;
	}

	// --- 主线程：放置 / 取消 ---

	/**
	 * 尝试放置建造计划：canPlace 通过且与未开工计划脚印不重叠才入池。
	 *
	 * @return true = 已入池
	 */
	public static boolean tryPlace(BuildingType block, int tx, int ty, int angle) {
		if (block == null || !block.canPlace(tx, ty, angle)) {
			return false;
		}
		synchronized (lock) {
			// 与未开工计划脚印重叠也拒（错开锚点/换建筑类型都不行——spawn 时会互相覆盖拆除；
			// 已 spawn 的脚印已被工地占了，canPlace 自然挡住）
			if (overlapsPlans(block, tx, ty, angle)) {
				return false;
			}
			Plan p = new Plan();
			p.kind = 0;
			p.block = block;
			p.tx = tx;
			p.ty = ty;
			p.angle = angle % 4;
			plans.add(p);
			return true;
		}
	}

	/**
	 * 与未开工计划的脚印是否重叠（主线程放置/预览用）。
	 *
	 * <p>同锚点只是重叠的一种；错开锚点但脚印相交（2×2 放 (5,5) 与 (6,6)）同样要拒。
	 */
	public static boolean overlapsPlans(BuildingType block, int tx, int ty, int angle) {
		synchronized (lock) {
			for (Plan q : plans) {
				if (q.kind != 0 || q.site != null || q.block == null) {
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

	/** 从前向后找第一个未认领的计划（池内无已完成项——完工即出池）。 */
	public static Plan firstUnclaimed() {
		synchronized (lock) {
			for (Plan p : plans) {
				if (p.main == null) {
					return p;
				}
			}
			return null;
		}
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
		boolean ok = block.canPlace(tx, ty, angle) && !overlapsPlans(block, tx, ty, angle);
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
	 * 计划池脚印（P2 最小可视化）：每个 kind=0 计划画半透明贴图 + 细框，
	 * 让"放下的计划"肉眼可见。锁内直画（临界区只有字段读；P3 由
	 * {@link #drawLinksAndFx} 升级为认领连线 + 进度环后取代）。
	 */
	public static void drawPlans() {
		synchronized (lock) {
			for (Plan p : plans) {
				// 已 spawn 的有真实建筑/工地渲染，不用再画脚印
				if (p.kind != 0 || p.block == null || p.site != null) {
					continue;
				}
				float ts = WorldData.TILE_SIZE;
				float psize = p.block.psize();
				Draw.color(Color.white, 0.25f);
				Draw.rect(p.block.region, p.tx * ts + psize / 2f, p.ty * ts + psize / 2f,
						psize, psize, p.angle * 90f);
				Draw.color(Color.sky, 0.5f);
				Lines.stroke(1f);
				// 整体外轮廓（与预览同款；已 spawn 的上面已跳过）
				drawFootprint(p.block, p.tx, p.ty, p.angle);
				Draw.color();
			}
		}
	}
}
