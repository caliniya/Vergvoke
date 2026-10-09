package caliniya.vergvoke.game.data;

import arc.util.pooling.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.system.world.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.world.*;

public class WorldData {
	public static World world;

	public static EntityAr<Unit> moveunits;
	public static EntityAr<Bullet> bullets;

	// --- 空间划分相关 ---
	public static final int CHUNK_SIZE = 32;
	public static final int TILE_SIZE = 32;
	public static final int CHUNK_PIXEL_SIZE = CHUNK_SIZE * TILE_SIZE;

	private WorldData() {
	}

	public static void initWorld(int w, int h, boolean space) {
		Game.team = TeamTypes.Evoke;

		// 实体容器是 static final（生成的 EntityArs），跨存档加载存活，这里必须显式清空
		EntityArs.Unit.clear();
		EntityArs.Building.clear();
		// 上一局的选中单位全是失效对象，一并清掉
		CommandData.init();
		// 上一局登记的待死实体可能已经回池，留着会让新一局对着别人的对象下杀手
		Entities.clearDead();

		moveunits = new EntityAr<>(unit -> unit.id);
		bullets = new EntityAr<>(bullet -> bullet.id);

		world = new World(w, h, space);
		world.init();

		Teams.init();

		RouteData.init();

		// 初始化四叉树覆盖范围
		float worldPixelW = world.W * TILE_SIZE;
		float worldPixelH = world.H * TILE_SIZE;
		initAllTrees(worldPixelW, worldPixelH);
	}

	public static void initAllTrees(float worldPixelW, float worldPixelH) {
		EntityArs.Unit.resize(0, 0, worldPixelW, worldPixelH);
		EntityArs.Building.resize(0, 0, worldPixelW, worldPixelH);
		if (moveunits != null)
			moveunits.resize(0, 0, worldPixelW, worldPixelH);
		if (bullets != null)
			bullets.resize(0, 0, worldPixelW, worldPixelH);
		// 同步子弹处理系统的内部子弹树（力场拦截等依赖它的 intersect）
		if (BulletProcess.it != null)
			BulletProcess.it.resizeTree(worldPixelW, worldPixelH);
	}

	/** 放置建筑 */
	public static Building placeBuilding(BuildingType block, int tx, int ty, int angle, TeamTypes team) {
		Building b = block.create(team, tx, ty, angle);
		placeTiles(b);
		// 容器注册：渲染/更新/索敌全走 EntityArs.Building，漏了建筑就会"消失"
		EntityArs.Building.add(b);
		return b;
	}

	/**
	 * 把读档还原的建筑落进世界（工厂 + read + rebuild 之后调）：
	 * 占位瓦片/区块注册 + 导航更新 + 入容器（此时坐标已定，四叉树直接插在正确位置）。
	 */
	public static void placeBuilding(Building b) {
		placeTiles(b);
		// 同上；坐标此时已定，四叉树直接插在正确位置
		EntityArs.Building.add(b);
	}

	/**
	 * 移除建筑
	 *
	 * <p>
	 * 导航清占必须在瓦片注销前：{@code RouteData.updateBlock(bx,by)} 靠 world.getBuilding 找建筑。
	 */
	public static void removeBuilding(Building b) {
		if (b == null || b.type == null)
			return; // type == null = 已回池（reset 置空的标志）
		if (world != null && b.type != null && b.type.solid) {
			RouteData.updateBlock(b.tx, b.ty);
		}
		if (world != null) {
			world.unregisterTiles(b);
		}
		EntityArs.Building.remove(b);
		b.id = Entities.freeID(b.id);
		Pools.free(b);
	}

	/** 同步四叉树 */
	public static void syncTree(Building b) {
		EntityArs.Building.move(b, b.x, b.y);
	}

	/**
	 * 设置环境方块
	 */
	public static void setEnvBlock(int x, int y, ENVBlock block) {
		if (world == null)
			return;
		world.setENVBlock(x, y, block);
		RouteData.updateBlock(x, y, block != null && block.solid);
	}

	/* 地图加载完成后的初始化 */

	/**
	 * 重建瞬态容器（寻路队列 / 子弹）并重刷四叉树范围。重载流程专用：
	 * 旧线程系统停透后调用——旧线程死前可能已把陈旧缓冲换进这些静态字段。
	 */
	public static void rebuildTransientContainers() {
		moveunits = new EntityAr<>(unit -> unit.id);
		bullets = new EntityAr<>(bullet -> bullet.id);
		Entities.clearDead();
		if (world != null) {
			initAllTrees(world.W * TILE_SIZE, world.H * TILE_SIZE);
		}
	}

	public static void mapLoaded() {
		if (world != null) {
			RouteData.init();
		}
	}

	/** 占位瓦片注册 + 覆盖拆除 + 导航标记（放置两条路径共用）。 */
	private static void placeTiles(Building b) {
		if (world == null)
			return;
		BuildingType blk = b.type;
		if (blk == null)
			return;

		b.getOccupiedCoords(
				(tx, ty) -> {
					if (tx < 0 || ty < 0 || tx >= world.W || ty >= world.H)
						return;
					Building existing = world.getBuilding(tx, ty);
					if (existing != null && existing != b) {
						removeBuilding(existing);
					}
					world.registerTile(tx, ty, b);
					RouteData.updateBlock(tx, ty, blk.solid);
				});
	}

	public static void clear() {
		EntityArs.Unit.clear(Entity::reset);
		EntityArs.Building.clear(Entity::remove);
		if (moveunits != null)
			moveunits.clear(Entity::reset);
		if (bullets != null)
			bullets.clear();
	}
}
