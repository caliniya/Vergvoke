package caliniya.vergvoke.type.type;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.util.*;
import arc.util.pooling.*;
import caliniya.vergvoke.base.api.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.module.*;
import caliniya.vergvoke.ui.*;

/**
 * 建筑的类型载体：配置与类型级行为，实体侧是 {@code @Entity} 生成的
 * {@link caliniya.vergvoke.base.ecs.Building}。
 * 建筑按瓦片放置，放置/移除编排统一走 {@link WorldData} 门面。
 */
public class BuildingType extends ContentType
		implements EntityType, DrawType<Building>, TechNodeContent {

	/** 像素边长（派生缓存 = size × TILE_SIZE，由 {@link #psize()} 刷新，别手写）。 */
	public float psize;

	/** 占地边长，格。 */
	public int size = 2;

	/** 允许建造。 */
	public boolean buildable = true;

	/** 阻挡通行（导航占位按它更新）。 */
	public boolean solid = true;

	/** 血上限。 */
	public float health = 100;

	/** 物品容量，0 = 不能存。 */
	public int capacity = 100;

	/** 允许存入的物品白名单，默认全部物品。 */
	public ItemType[] allowItem = Contents.items;

	/** 液体容量（0 = 不能存液体）。 */
	public float liquidCapacity;

	/** 电力电池容量（0 = 不能存电力）。 */
	public float powerCapacity;

	/** 主贴图（load 时从图集取，缺图兜底白块）。 */
	public TextureRegion region;

	/** 占位格子偏移，成对存 dx, dy（null = 方正占位）；放置/读档时按角度旋转后灌进实体。 */
	public int[] shapeOffsets = null;

	public BuildingType(String name) {
		super(name, CType.Block);
	}

	@Override
	public TechNodeContent[] requirements() {
		return requirements; // ContentType 里的前置字段（默认 null）
	}

	@Override
	public void load() {
		// 带兜底：贴图没画完/没进图集的建筑用白块，不让整局崩掉
		region = Core.atlas.find(name, "white");
	}

	/** 建筑像素边长：由 size 推导，每次调用顺手刷新缓存。 */
	public float psize() {
		psize = size * WorldData.TILE_SIZE;
		return psize;
	}


	public Building create(TeamTypes team, int tx, int ty, int angle) {
		float psize = psize();

		Building b = Pools.obtain(Building.class, Building::new);
		b.type = this;

		// BlockComp：占位
		b.tx = tx;
		b.ty = ty;
		b.angle = angle % 4;
		b.tileSize = size;
		b.shapeOffsets = shapeOffsets != null
				? getRotatedOffsets(b.angle, shapeOffsets)
				: null;

		// 派生坐标 / 战斗基础
		b.x = tx * WorldData.TILE_SIZE + psize / 2f;
		b.y = ty * WorldData.TILE_SIZE + psize / 2f;
		b.size = psize;
		b.team = team;
		b.teamData = team != null ? team.data() : null;
		b.maxHealth = health;
		if (b.health <= 0f) {
			b.health = health;
		}
		b.id = Entities.assignID();

		// 容量模块：对象池复用时不重建，保留原有存货
		if (b.item == null && capacity > 0) {
			b.item = new ItemModule(capacity);
			b.item.setFilter(allowItem);
		}
		if (b.liquid == null && liquidCapacity > 0) {
			b.liquid = new LiquidModule(liquidCapacity);
		}
		if (b.power == null && powerCapacity > 0) {
			b.power = new PowerModule(powerCapacity);
		}

		// 配方组：默认无（CraftComp 约定：非生产建筑 stack 为 null）；有配方的子类覆写 create 自行装配
		b.stack = null;

		b.self = b;

		// 运行时状态：对象池复用时这些残留会串剧本，逐帧复位
		b.reload = 0f;
		b.rotation = 0f;
		b.target = null;
		b.angleToTarget = 0f;
		b.retargetTimer = 0f;
		b.filter = null;

		// 容器注册 / 瓦片注册 / 导航更新由 WorldData 门面编排（placeBuilding）
		return b;
	}

	/**
	 * 读档后重算派生数据（形状/坐标/血上限/阵营）：组件 {@code @Read} 只恢复原始字段，
	 * 派生数据要用类型配置。之后由调用方走 {@code WorldData.placeBuilding} 完成注册。
	 */
	public void rebuild(Building b) {
		float psize = psize();

		b.angle = b.angle % 4;
		b.tileSize = size;
		b.shapeOffsets = shapeOffsets != null
				? getRotatedOffsets(b.angle, shapeOffsets)
				: null;
		b.x = b.tx * WorldData.TILE_SIZE + psize / 2f;
		b.y = b.ty * WorldData.TILE_SIZE + psize / 2f;
		b.size = psize;
		b.maxHealth = health;
		b.teamData = b.team != null ? b.team.data() : null;
		b.self = b;
	}

	/** 建筑按瓦片放置，不走世界坐标创建；真要采样居中位置用 {@link #create(TeamTypes, int, int, int)} 的 tx/ty。 */
	@Override
	public Entity<?, ?> create(TeamTypes team, float x, float y) {
		throw new UnsupportedOperationException("建筑按 tx/ty/angle 放置，走 create(team, tx, ty, angle)");
	}

	// --- 类型级钩子（EntityType）---

	/** 类型级每帧钩子：给非炮塔建筑的自身行为留入口，默认空。子类覆写这个，不用碰 Entity 重载。 */
	public void update(Building b, float dt) {
	}

	/// EntityType 分发入口：判型后转 {@link #update(Building, float)}。
	@Override
	public void update(Entity<?, ?> entity, float dt) {
		if (entity instanceof Building b) {
			update(b, dt);
		}
	}

	/** 类型级绘制：主贴图按朝向角绘制（angle × 90°），炮塔等子类覆写。 */
	@Override
	public void draw(Building b) {
		Draw.rect(region, b.x, b.y, b.angle * 90f);
	}

	/// EntityType 分发入口：判型后转 {@link #draw(Building)}。
	@Override
	public void draw(Entity<?, ?> entity) {
		if (entity instanceof Building b) {
			draw(b);
		}
	}

	/// 调试绘制：绿框包围盒、青框异形占位格、上方坐标与血量文字。
	@Override
	public void drawDebug(Building b) {
		BuildingType t = b.type;
		float psize = t != null ? t.psize : b.tileSize * WorldData.TILE_SIZE;

		Draw.color(Color.green);
		Lines.stroke(4f);
		// 绘制基于 size 的包围盒
		Lines.rect(b.x - psize / 2, b.y - psize / 2, psize, psize);

		// 异形建筑的实际占位格，比包围盒精确
		if (b.shapeOffsets != null) {
			Draw.color(Color.cyan);
			Lines.stroke(1f);
			for (int i = 0; i < b.shapeOffsets.length; i += 2) {
				float tx = (b.tx + b.shapeOffsets[i]) * WorldData.TILE_SIZE;
				float ty = (b.ty + b.shapeOffsets[i + 1]) * WorldData.TILE_SIZE;
				Lines.rect(tx, ty, WorldData.TILE_SIZE, WorldData.TILE_SIZE);
			}
		}

		// 坐标与血量文字
		Fonts.def.draw(
				b.x + "   " + b.y, b.x + psize / 2f, b.y + psize + 10f, Align.center);
		Fonts.def.draw(
				Strings.format("" + b.health),
				b.x - b.tileSize,
				b.y + 8f,
				Align.center);
		Draw.color(); // 重置颜色
	}

	/** 摧毁：编排顺序封在 {@code WorldData.removeBuilding}。 */
	@Override
	public void remove(Entity<?, ?> entity) {
		if (entity instanceof Building b) {
			WorldData.removeBuilding(b);
		}
	}


	/** 覆写物品白名单（默认全部物品）。 */
	public void allowAllItem(ItemType... types) {
		allowItem = types;
	}

	/**
	 * 按角度 (0-3) 旋转占位格子偏移，确定建筑实际占格。
	 *
	 * @param baseOffsets 原始形状偏移（通常是 {@link #shapeOffsets}）
	 *
	 * @return 旋转后的新数组；入参 null 时返回 null，不修改入参
	 */
	public static int[] getRotatedOffsets(int angle, int[] baseOffsets) {
		if (baseOffsets == null)
			return null;

		int[] rotated = baseOffsets.clone();

		for (int i = 0; i < rotated.length; i += 2) {
			int x = baseOffsets[i];
			int y = baseOffsets[i + 1];

			switch (angle) {
				case 1:
					rotated[i] = y;
					rotated[i + 1] = -x;
					break;
				case 2:
					rotated[i] = -x;
					rotated[i + 1] = -y;
					break;
				case 3:
					rotated[i] = -y;
					rotated[i + 1] = x;
					break;
				default:
					rotated[i] = x;
					rotated[i + 1] = y;
					break;
			}
		}
		return rotated;
	}
}
