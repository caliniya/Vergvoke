package caliniya.vergvoke.system.game;

import arc.struct.*;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.world.building.*;

// 在这里进行主线程游戏内容的更新
@SystemDef(name = "GameProcess", thread = "main", index = 5)
public class GameProcess extends caliniya.vergvoke.system.System<GameProcess> {

	/**
	 * 全局实例（由 Data.loadSystems() 创建）。
	 */
	public static GameProcess it;

	public Ar<Unit> deadUnits;
	public Ar<Building> deadBuildings;
	/**
	 * 待销毁实体（伤害结算可能在后台线程登记，真正的销毁只在这里做）。
	 */
	public Ar<Entity> freshKilled;

	/** 建造副作用缓冲（复用，避免每帧 new）。 */
	private final Ar<BuildPlan.Pend> buildPend = new Ar<>();

	@Override
	public GameProcess init() {
		index = 5;
		deadUnits = new Ar<>();
		deadBuildings = new Ar<>();
		freshKilled = new Ar<>();
		return super.init(false);
	}

	@Override
	public void update(float delta) {
		// 建造系统副作用队列：ai 线程只登记，这里锁外执行（SPAWN/FINISH 碰实体生命周期，
		// 持 plans 锁去拿 EntityArs 写锁有死锁风险——drain 已保证锁内只拷快照）
		BuildPlan.drain(buildPend);
		for (BuildPlan.Pend e : buildPend) {
			if (e.op == BuildPlan.SPAWN) {
				spawnSite(e.plan);
			} else if (e.op == BuildPlan.FINISH) {
				finishSite(e.plan);
			} else if (e.op == BuildPlan.DEMO) {
				demolish(e.plan);
			}
		}
		buildPend.clear();

		// 先处理刚判死的实体（延迟最小化，防止血量变负才死）。
		// 这是**唯一的销毁出口**：伤害结算可能在 BulletProcess 线程发生，那里只登记不销毁，
		// 注销容器 / 回收 ID / 回池一律在这里（主线程）做。
		Entities.drainDead(freshKilled);
		for (Entity e : freshKilled) {
			e.kill();
		}
		freshKilled.clear();

		// 读锁遍历执行逻辑（update 只更新位置字段，不写四叉树，避免读锁内写锁死锁）
		Ar<Unit> moved = new Ar<>();
		EntityArs.Unit.each(
				u -> {
					if (u == null) {
						return;
					}
					if (u.health <= 0) {
						deadUnits.add(u);
						return;
					} else {
						u.update(delta);
						u.canShoot = true;
						u.updateWeapons(delta);
						if (u.velocityDirty) {
							moved.add(u);
						}
					}
				});
		// 对位置变化的单位逐个短暂写锁更新四叉树（写锁不长时间持有，读方几乎不阻塞）
		for (Unit u : moved) {
			EntityArs.Unit.move(u, u.x, u.y);
			u.velocityDirty = false;
		}
		for (Unit u : deadUnits) {
			u.kill();
		}
		deadUnits.clear();

		EntityArs.Building.each(
				b -> {
					if (b == null) {
						return;
					}
					if (b.health <= 0) {
						deadBuildings.add(b);
						return;
					} else {
						b.update(delta);
						// 类型级钩子（BuildingType.update，给非炮塔的自有行为留入口）
						if (b.type != null) {
							b.type.update(b, delta);
						}
					}
				});
		for (Building b : deadBuildings) {
			b.kill();
		}
		deadBuildings.clear();
	}

	/** SPAWN：主线程 spawn 工地（建造中的建筑血量压到 1 点——{@code health<=0} 即死判定避开 0）。 */
	private void spawnSite(BuildPlan.Plan p) {
		if (p.done) {
			return; // 提交后、drain 前被玩家取消：别 spawn 出没人管的孤儿工地
		}
		if (p.site != null && p.site.type != null) {
			return; // 防重（单位在半径内每帧都想提交）
		}
		// 读档恢复的工地计划：位置已有半成品建筑（buildings 段恢复的）→ 直接关联，别重复 spawn
		Building exist = WorldData.world != null ? WorldData.world.getBuilding(p.tx, p.ty) : null;
		if (exist != null && exist.type == p.block) {
			p.site = exist;
			p.spawning = false;
			return;
		}
		Building b = WorldData.placeBuilding(p.block, p.tx, p.ty, p.angle, p.team);
		b.health = 1f;
		b.finished = false; // 建造中：无功能、进度可视化（create 默认成品，工地显式压下）
		p.site = b;
		p.spawning = false;
	}

	/** FINISH：工地转正（满血 + finished 标记），计划出池。 */
	private void finishSite(BuildPlan.Plan p) {
		Building b = p.site;
		if (b == null || b.type == null) {
			return; // 工地已被摧毁：BuildAi 自检 site 失效后会重新 spawn
		}
		b.health = b.maxHealth;
		b.finished = true;
		BuildPlan.cancel(p); // 出池 + 解绑认领（BuildAi 下帧自检清 cur）
	}

	/** DEMO：拆除完成，主线程移除建筑 + 计划出池。 */
	private void demolish(BuildPlan.Plan p) {
		Building b = p.building;
		if (b != null && b.type != null) {
			WorldData.removeBuilding(b);
		}
		BuildPlan.cancel(p);
	}
}
