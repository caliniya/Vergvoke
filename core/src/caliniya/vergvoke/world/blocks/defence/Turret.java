package caliniya.vergvoke.world.blocks.defence;

import arc.graphics.g2d.Lines;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.Core;
import caliniya.vergvoke.core.meta.ui.Pal;
import caliniya.vergvoke.base.ecs.Building;
import caliniya.vergvoke.type.type.BulletType;
import caliniya.vergvoke.world.Block;

/**
 * 炮塔：只保留「配置 + 绘制」，行为已全部迁标注给组件
 * （TargetComp 索敌、TurretComp 瞄准/装填/开火），不再有 update / 一套自己的 write。
 *
 * <p>配置项在放置时由 {@code BuildingType.create} 拷进实例
 * （range / rotateSpeed / reloadTime / bulletType）。
 */
public class Turret extends Block {

    public float range = 400f;
    public float rotateSpeed = 500f;
    public float reloadTime = 10f;
    public BulletType bulletType;

    public TextureRegion baseRegion;

    public Turret(String name) {
        super(name);
        this.capacity = 0;
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
        if (bulletType != null)
            bulletType.load();
    }

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
