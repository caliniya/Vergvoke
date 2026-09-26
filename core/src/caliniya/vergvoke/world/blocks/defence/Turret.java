package caliniya.vergvoke.world.blocks.defence;

import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.Core;
import arc.math.Mathf;
import arc.util.io.Writes;
import arc.util.io.Reads;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.core.meta.ui.Pal;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.type.BulletType;
import caliniya.vergvoke.world.Block;

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
    public void update(Building b, float dt) {
        // 索敌随主线程块更新驱动（索敌线程已退役）：失效 / 超射程 → 重搜
        if (b.target == null
                || b.target.health <= 0
                || Mathf.dst2(b.x, b.y, b.target.x, b.target.y) > range * range) {
            b.target = findTarget(b);
        }

        // 瞄准与射击
        if (b.target != null) {
            float targetAngle = Angles.angle(b.x, b.y, b.target.x, b.target.y);
            b.rotation = Angles.moveToward(b.rotation, targetAngle, rotateSpeed * dt);

            b.reload += dt;

            if (b.reload >= reloadTime && Angles.angleDist(b.rotation, targetAngle) < 5f) {
                shoot(b, b.rotation);
                b.reload = 0;
            }
        }
    }

    @Override
    public void draw(Building b) {
        Draw.rect(baseRegion, b.x, b.y, b.angle * 90f);
        Draw.rect(region, b.x, b.y, b.rotation - 90f);
    }

    private void shoot(Building b, float angle) {
        float x = b.x;
        float y = b.y;
        Bullet.create(bulletType, b, x, y, angle, 0, 0);
    }

    /** 覆写 */
    @Override
    public Entity findTarget(Building b) {
        return Entities.closestEnemy(b.team, b.x, b.y, range);
    }

    @Override
    public void write(Building b, Writes w) {
        w.f(b.rotation);
        w.f(b.reload);
    }

    @Override
    public void read(Building b, Reads r) {
        b.rotation = r.f();
        b.reload = r.f();
    }

    @Override
    public void drawDebug(Building b) {
        super.drawDebug(b);
        Draw.color(Pal.light);
        Lines.circle(b.x, b.y, range);
    }
}
