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
import caliniya.vergvoke.type.def.craft.recipe.*;
import caliniya.vergvoke.type.module.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.world.blocks.defence.*;
import caliniya.vergvoke.world.blocks.production.*;

/**
 * 建筑的类型载体（对齐 UnitType 的模式）：实体类型链接走基类 {@code Entity.type}，
 * 工厂在类型上（{@link #create}），实体侧不再有 block 引用字段。
 *
 * <p>原 {@code world.Block} 已并入本类：基础配置 / 贴图 / 绘制 / 形状定义都在这里，
 * 统一走 {@code b.type.xxx}。炮塔（{@link Turret}）与工厂（{@link Factory}）作为子类补充各自配置。
 */
public class BuildingType extends ContentType
		implements EntityType, DrawType<Building>, TechNodeContent {

	// --- 基础属性 ---
	public float psize; // 大小，像素级
	public int size = 2; // 大小，单位格
	public boolean buildable = true; // 可以造
	public boolean solid = true; // 可以阻挡通行
	public float health = 100; // 顾名思义
	public int capacity = 100; // 物品容量，为0就是不能存
	public ItemType[] allowItem = Contents.items; // 能存的,默认啥都能存一百

	/** 液体容量（0 = 不能存液体）。 */
	public float liquidCapacity;

	/** 电力电池容量（0 = 不能存电力）。 */
	public float powerCapacity;

	public TextureRegion region; // 主贴图

	// --- 形状定义 ---
	// 相对于锚点(0,0)的偏移量数组：[dx1, dy1, dx2, dy2, ...]
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

	/** 建筑像素边长（顺手把 psize 刷到最新，省得忘记同步）。 */
	public float psize() {
		psize = size * WorldData.TILE_SIZE;
		return psize;
	}

	/** 类型级工厂：按 tx/ty/angle 放置（@Init/@Reset 缺位，派生初始化先在这里手动做）。 */
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

		// TurretComp：配置拷贝（仅炮塔类建筑）；索敌半径走 TargetComp.range
		if (this instanceof Turret t) {
			b.reloadTime = t.reloadTime;
			b.rotateSpeed = t.rotateSpeed;
			b.bullet = t.bulletType;
			b.range = t.range;
		}

		// CraftComp：配方组装（深拷贝 content 共享模板 + 绑定运行时模块；池化复用不残留）
		// 非工厂建筑显式置空，避免上一任占用者留下的 RecipeStack 串剧本
		b.stack = null;
		if (this instanceof Factory f) {
			b.stack = new RecipeStack();
			for (Recipe r : f.recipes) {
				Recipe copy = r.copy();
				copy.bind(b.item, b.liquid, b.power);
				b.stack.recipes.add(copy);
			}
			// 默认选第一条配方（有配方才开工）
			b.stack.current = f.recipes.isEmpty() ? -1 : 0;
		}

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
	 * 读档恢复完毕后再算一遍派生数据：占位形状 / 中心坐标 / 血上限 / 阵营数据。
	 *
	 * <p>组件的 {@code @Read} 只能恢复原始字段——派生数据要用类型配置，而 {@code @Import}
	 * 借不到基类那个泛型 {@code type} 字段，所以这一步留在类型侧，由给档路径显式调用。
	 *
	 * <p>坐标在 read 后才最终确定，容器注册（含四叉树插入）由调用方接着调
	 * {@code WorldData.placeBuilding(b)} 完成——树直接插在正确位置，不存在 (0,0) 旧节点。
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

	/** 建筑按瓦片放置，不走按世界坐标创建；真要采样居中位置用 {@link #create} 的 tx/ty。 */
	@Override
	public Entity<?, ?> create(TeamTypes team, float x, float y) {
		throw new UnsupportedOperationException("建筑按 tx/ty/angle 放置，走 create(team, tx, ty, angle)");
	}

	// --- 类型级钩子（EntityType）---

	/** 类型级每帧钩子：给非炮塔建筑的自身行为留入口，默认空。 */
	public void update(Building b, float dt) {
	}

	@Override
	public void update(Entity<?, ?> entity, float dt) {
		if (entity instanceof Building b) {
			update(b, dt);
		}
	}

	/** 类型级绘制：建筑图标（炮塔等子类覆写）。 */
	@Override
	public void draw(Building b) {
		Draw.rect(region, b.x, b.y, b.angle * 90f);
	}

	@Override
	public void draw(Entity<?, ?> entity) {
		if (entity instanceof Building b) {
			draw(b);
		}
	}

	@Override
	public void drawDebug(Building b) {
		BuildingType t = b.type;
		float psize = t != null ? t.psize : b.tileSize * WorldData.TILE_SIZE;

		Draw.color(Color.green);
		Lines.stroke(4f);
		// 绘制基于 size 的包围盒
		Lines.rect(b.x - psize / 2, b.y - psize / 2, psize, psize);

		// 3. 绘制占据的实际格子 (黄色细线)
		// 对于异形建筑，这比包围盒更准确
		if (b.shapeOffsets != null) {
			Draw.color(Color.cyan);
			Lines.stroke(1f);
			for (int i = 0; i < b.shapeOffsets.length; i += 2) {
				float tx = (b.tx + b.shapeOffsets[i]) * WorldData.TILE_SIZE;
				float ty = (b.ty + b.shapeOffsets[i + 1]) * WorldData.TILE_SIZE;
				Lines.rect(tx, ty, WorldData.TILE_SIZE, WorldData.TILE_SIZE);
			}
		}

		// 4. 绘制旋转角度 (青色文字)
		Fonts.def.draw(
				b.x + "   " + b.y, b.x + psize / 2f, b.y + psize + 10f, Align.center);
		Fonts.def.draw(
				Strings.format("" + b.health),
				b.x - b.tileSize,
				b.y + 8f,
				Align.center);
		Draw.color(); // 重置颜色
	}

	/** 摧毁：编排顺序（导航清占 → 瓦片注销 → 容器 → ID → 回池）封在 {@code WorldData.removeBuilding}。 */
	@Override
	public void remove(Entity<?, ?> entity) {
		if (entity instanceof Building b) {
			WorldData.removeBuilding(b);
		}
	}

	// --- 物品相关 ---

	public void allowAllItem(ItemType... types) {
		allowItem = types;
	}

	/**
	 * 辅助方法：获取旋转后的形状偏移量 这用于确定建筑在当前角度下实际占据了哪些格子
	 *
	 * @param angle       建筑当前角度 (0-3)
	 * @param baseOffsets 原始形状偏移 (通常是 {@link #shapeOffsets})
	 *
	 * @return 旋转后的新偏移量数组
	 */
	public static int[] getRotatedOffsets(int angle, int[] baseOffsets) {
		if (baseOffsets == null)
			return (int[]) null;

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
