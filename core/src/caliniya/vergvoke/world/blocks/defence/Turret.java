package caliniya.vergvoke.world.blocks.defence;

import arc.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.type.type.*;

/**
 * 炮塔:战斗配置载体,放置时由覆写的 {@link #create} 拷进实例。
 */
public class Turret extends BuildingType {

	/** 索敌/开火半径。 */
	public float range = 400f;

	/** 炮塔转向速度。 */
	public float rotateSpeed = 500f;

	/** 开火间隔。 */
	public float reloadTime = 10f;

	/** 发射的子弹类型（null = 不开火）。 */
	public BulletType bulletType;

	/** 底座贴图（不随炮管旋转）；主贴图 region 是旋转部分。 */
	public TextureRegion baseRegion;

	public Turret(String name) {
		super(name);
		this.capacity = 0; // 炮塔不存物品
	}

	@Override
	public void load() {
		super.load();
		baseRegion = Core.atlas.find(name + "-base");
		if (bulletType != null) {
			bulletType.load();
		}
	}

	/** 追加把战斗参数拷进实例（TurretComp / TargetComp 运行时读这些字段）。 */
	@Override
	public Building create(TeamTypes team, int tx, int ty, int angle) {
		Building b = super.create(team, tx, ty, angle);
		b.reloadTime = reloadTime;
		b.rotateSpeed = rotateSpeed;
		b.bullet = bulletType;
		b.range = range;
		return b;
	}

	/** 底座按放置角绘制，炮管部分按实时 rotation。 */
	@Override
	public void draw(Building b) {
		Draw.rect(baseRegion, b.x, b.y, b.angle * 90f);
		Draw.rect(region, b.x, b.y, b.rotation - 90f);
	}

	@Override
	public void drawDebug(Building b) {
		super.drawDebug(b);
		Draw.color(Pal.light);
		Lines.circle(b.x, b.y, range);
	}
}
