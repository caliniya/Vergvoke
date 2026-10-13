package caliniya.vergvoke.type.ai;

import arc.math.*;
import arc.math.geom.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.world.building.*;
import caliniya.vergvoke.world.building.BuildPlan.*;

/**
 * 建造 AI：认领 → 前往 → spawn 工地 → 推进扣物品 → 完工；拆除是同一套逻辑的反向。
 *
 * <p>线程纪律（ai 线程）：只思考 + 写白名单——自身字段、{@code plan.main}（锁内，经
 * {@link BuildPlan} 静态方法）、{@code moveunits}（synchronized 既有锁）、单位 {@code item}、
 * 工地 {@code progress}；实体生命周期副作用（SPAWN/FINISH/DEMO）一律 {@code BuildPlan.submit}
 * 登记，主线程 {@code GameProcess} drain 执行。
 *
 * <p>身份区分：{@code cur.main == u} = 主建造者（扣/收物品）；否则是到场协助者（只加速度，
 * 物品问主单位要——主缺料全员停工，避免免费加速）。卡住（缺料/不可达/走远/背包满）超
 * {@link #STARVE_TIME} 秒放弃：保留工地与进度，只释放认领，让别的单位能接手。
 */
public class BuildAi extends Ai {

	/// 卡住多久放弃（秒）：缺料等补给 / 走不到 / 被击退回不来 / 背包满收不下
	public static final float STARVE_TIME = 3f;

	/// 认领扫描计时：空闲时累加，到 0.5s 找一次首个未认领计划
	public float timer;

	/// 物品消耗浮点累计：跨帧累计，满 1 扣 1（取整去小数，避免每帧截断为 0）
	public float consumed;

	/// 拆除返还浮点累计（同 consumed 节奏，满 1 收 1）
	public float refunded;

	/// 当前建造/拆除的计划
	public Plan cur;

	/// 玩家指定的目标（指挥下令；优先于自动认领；完成/取消/放弃后自清）
	public Plan orderedPlan;

	/// 卡住计时（缺料/不可达/走远）
	private float starve;

	/// 已对当前目标设过移动目标（避免每帧重复写 moveunits）
	private boolean routed;

	/// 重寻路限流计时（到达态却又超范围——被击退等——每 1s 才重试一次）
	private float retry;

	@Override
	public void update(Unit u, float dt) {
		// cur 有效性自检：认领者看 main==u；指定目标看计划是否已出池
		if (cur != null) {
			// 拆除目标建筑没了（被打爆/已被拆）：计划作废出池
			if (cur.kind == 1 && cur.building != null && cur.building.type == null) {
				BuildPlan.cancel(cur);
				cur = null;
				routed = false;
				starve = 0f;
			} else {
				boolean valid = (cur == orderedPlan) ? !cur.done : cur.main == u;
				if (!valid) {
					cur = null;
					routed = false;
					starve = 0f;
				}
			}
		}

		// 玩家指定目标优先（命令前往/协助建造）
		if (orderedPlan != null) {
			if (orderedPlan.done) {
				orderedPlan = null; // 已完成/取消
			} else if (cur != orderedPlan) {
				if (cur != null && cur.main == u) {
					BuildPlan.release(cur, u); // 放手头认领的计划
				}
				cur = orderedPlan;
				routed = false;
				starve = 0f;
			}
		}

		if (cur == null) {
			if (ordered || !u.canBuild || u.health <= 0f) {
				return;
			}
			timer += dt;
			if (timer < 0.5f) {
				return;
			}
			timer = 0f;
			cur = BuildPlan.claimFirst(u);
			routed = false;
			return;
		}

		if (cur.kind == 0) {
			updateBuild(u, dt);
		} else {
			updateDemolish(u, dt);
		}
	}

	/** 建造推进：靠近 → 到位登记 SPAWN → 扣物品推进 → 满进度登记 FINISH。 */
	private void updateBuild(Unit u, float dt) {
		Plan p = cur;
		float ts = WorldData.TILE_SIZE;
		float cx = (p.tx + p.block.size / 2f) * ts;
		float cy = (p.ty + p.block.size / 2f) * ts;
		float psize = p.block.size * ts;

		// 工地被摧毁（reset 置空 type）：重置等重新 spawn（位置仍合法的话）
		if (p.site != null && p.site.type == null) {
			p.site = null;
			routed = false;
			return;
		}

		if (!walkTo(u, p, cx, cy, psize, dt)) {
			return;
		}

		if (p.site == null) {
			// 到位但工地还没 spawn：登记（spawning 防重复）
			if (!p.spawning) {
				p.spawning = true;
				BuildPlan.submit(BuildPlan.SPAWN, p); // 主线程 spawn 工地
			}
			return; // 等主线程把工地 spawn 出来再推进
		}

		// 工地已存在且在场：推进。物品只从主单位扣；主缺料（或没有主——死了/被命令走开）
		// = 全员停工，避免免费加速。主没了时在场者超时放弃后计划回池（main=null），
		// 会被其他空闲单位重新认领，自愈。
		Unit payer = p.main;
		boolean hasMat = payer != null && payer.item != null
				&& payer.item.getAmount(p.site.cost.type) > 0;
		if (!hasMat) {
			starve += dt;
			giveUpIfStarved(p, u);
			return;
		}

		float d = u.buildSpeed * dt;

		// 主单位：消耗 = cost.amount × d ÷ buildTime ÷ buildEfficiency（>1 消耗更少）；取整去小数跨帧累计
		if (payer == u) {
			consumed += p.site.cost.amount * d / p.site.buildTime / u.buildEfficiency;
			while (consumed >= 1f) {
				if (u.item.removeItem(p.site.cost.type, 1) == 0) {
					starve += dt;
					giveUpIfStarved(p, u);
					return; // 缺料钳住：consumed 保留欠账
				}
				consumed -= 1f;
			}
		}

		starve = 0f;
		p.site.progress += d; // 协助者到场即加速度（各写各的，天然累加）
		if (p.site.progress >= p.site.buildTime) {
			p.site.progress = p.site.buildTime;
			BuildPlan.submit(BuildPlan.FINISH, p);
			// 立即解放接下一个活：FINISH 的转正/出池由主线程 drain 做，
			// 不清的话到 drain 前每帧都会重复提交
			cur = null;
			routed = false;
			if (orderedPlan == p) {
				orderedPlan = null;
			}
		}
	}

	/** 拆除推进（建造的反向）：靠近 → 按拆除进度持续返还 → 满进度登记 DEMO。 */
	private void updateDemolish(Unit u, float dt) {
		Plan p = cur;
		Building b = p.building;
		if (b == null || b.type == null) {
			// 建筑已经没了（被摧毁/已拆）：计划作废出池
			BuildPlan.cancel(p);
			cur = null;
			return;
		}

		if (!walkTo(u, p, b.x, b.y, b.size, dt)) {
			return;
		}

		// 和建造侧对称：返还只经主单位收（多人各收一份会超发物品）；
		// 主收不下（背包满）或没有主（死了/被命令走开）= 全员停工，主没了则超时放弃后回池自愈
		Unit payer = p.main;
		boolean canTake = payer != null && payer.item != null
				&& payer.item.accepts(b.cost.type)
				&& payer.item.getAmount(b.cost.type) < payer.item.capacity;
		if (!canTake) {
			starve += dt;
			giveUpIfStarved(p, u);
			return;
		}

		float d = u.buildSpeed * dt;

		// 主单位：整段拆除共返还 cost × 建成比例 × 返还系数，按拆除进度线性给（取整去小数同 consumed）。
		// 建成比例：工地按在建进度算（拆半成品只返已投入的部分），成品 = 1
		if (payer == u) {
			float built = b.buildTime > 0f ? Math.min(1f, b.progress / b.buildTime) : 1f;
			refunded += b.cost.amount * built * Game.deconstructRefund * (d / b.buildTime);
			while (refunded >= 1f) {
				if (u.item.addItem(b.cost.type, 1) == 0) {
					starve += dt;
					giveUpIfStarved(p, u);
					return; // 背包满钳住：refunded 保留欠账
				}
				refunded -= 1f;
			}
		}

		starve = 0f;
		p.progress += d; // 协助者到场即加速度（各写各的，天然累加）
		if (p.progress >= b.buildTime) {
			p.progress = b.buildTime;
			BuildPlan.submit(BuildPlan.DEMO, p);
			cur = null;
			routed = false;
			if (orderedPlan == p) {
				orderedPlan = null;
			}
		}
	}

	/**
	 * 靠近目标（不在建造半径内时寻路到"最大建造距离"边缘点）。
	 *
	 * @return true = 已在半径内（就位）；false = 还在路上/不可达（已累加 starve 或限流中）
	 */
	private boolean walkTo(Unit u, Plan p, float cx, float cy, float psize, float dt) {
		float dx = Math.max(Math.abs(u.x - cx) - psize / 2f, 0f);
		float dy = Math.max(Math.abs(u.y - cy) - psize / 2f, 0f);
		if (dx * dx + dy * dy <= u.buildRange * u.buildRange) {
			return true;
		}
		// 还在路上就不重复设目标；到达态却又超范围（被击退等）限流重试
		boolean moving = WorldData.moveunits.array.contains(u);
		if (routed && moving) {
			return false;
		}
		if (routed) {
			retry += dt;
			if (retry < 1f) {
				return false;
			}
			retry = 0f;
			routed = false; // 到了却离远了：重新规划靠近
		}
		// 停在最大建造距离：目标点取中心朝单位方向回退 0.9×buildRange 处。
		// 贴脸会被 solid 建筑卡住走不出来（寻路起点变固体格）；未来减速机制会让单位在这段"滑行"一小段
		float ang = Angles.angle(u.x, u.y, cx, cy);
		float gx = cx - Angles.trnsx(ang, u.buildRange * 0.9f);
		float gy = cy - Angles.trnsy(ang, u.buildRange * 0.9f);
		Vec2 dst = RouteData.nearestPassable(gx, gy, Mathf.ceil(u.sizet));
		if (dst == null) {
			starve += dt;
			giveUpIfStarved(p, u);
			return false; // 不可达：计划留在池里，换个单位再试
		}
		synchronized (WorldData.moveunits) {
			u.targetX = dst.x;
			u.targetY = dst.y;
			u.pathed = false;
			u.path = null;
			if (!WorldData.moveunits.array.contains(u)) {
				WorldData.moveunits.add(u);
			}
		}
		routed = true;
		return false;
	}

	/**
	 * 卡住超时放弃：保留工地/建筑与进度，只释放认领标记（main 归 null 后别的单位能接手）。
	 * 工地都还没 spawn 的（走不到）则整条放弃，计划回池等人认领。
	 */
	private void giveUpIfStarved(Plan p, Unit u) {
		if (starve < STARVE_TIME) {
			return;
		}
		starve = 0f;
		if (p.main == u) {
			BuildPlan.release(p, u); // 只解绑不出池——工地/建筑和进度留着
		}
		if (orderedPlan == p) {
			orderedPlan = null; // 指定目标也放弃（玩家可重新下令）
		}
		cur = null;
		routed = false;
	}

	@Override
	public Ai copy() {
		return new BuildAi();
	}
}
