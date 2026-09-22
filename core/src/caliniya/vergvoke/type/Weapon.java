package caliniya.vergvoke.type;

import arc.math.Angles;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.type.type.WeaponType;

public class Weapon {
    public final WeaponType type;
    public final Unit owner;

    public Entity<?, ?> target;

    public float rotation;
    public float reloadTimer = 0f;

    public boolean rotate;

    // 武器挂载点的世界坐标
    public float wx;
    public float wy;

    // 目标绝对角度 (从武器位置指向目标位置)
    public float targetAngle;

    // 目标相对角度 (目标相对于单位朝向的角度)
    public float mountAngle;

    public Weapon(WeaponType type, Unit owner) {
        this.type = type;
        this.owner = owner;
        this.rotation = 0f;
        this.rotate = type.rotate;

        if (type.isMirror && type.alternate) {
            this.reloadTimer = type.reload / 2f;
        }
    }

    // 如果第2个参数为假 说明单位武器被瘫痪 不能射击
    public void update(float dt, boolean can) {
        // 武器挂载点位置始终跟随单位（过热锁定时被击退，武器也要跟着移动）
        wx = owner.x + Angles.trnsx(owner.rotation, type.x, type.y);
        wy = owner.y + Angles.trnsy(owner.rotation, type.x, type.y);

        if (owner.locked)
            return; // 锁定：不开火不转向，但位置已更新
        // 冷却逻辑
        if (reloadTimer > 0) {
            reloadTimer -= dt;
        }

        if (!can)
            return;

        // 旋转逻辑
        if (rotate) {
            if (target != null) {
                // 炮塔：尝试旋转以对准目标
                // 计算目标绝对角度 (从武器位置指向目标位置)
                targetAngle = Angles.angle(wx, wy, target.x, target.y);
                // 计算目标相对角度 (目标相对于单位朝向的角度)
                mountAngle = targetAngle - owner.rotation - 90;
                this.rotation = Angles.moveToward(this.rotation, mountAngle, type.rotateSpeed * dt);
            } else {
                // 没有目标：平滑旋转归位到正前方
                this.rotation = Angles.moveToward(this.rotation, 0f, type.rotateSpeed * dt);
            }
        } else {
            // 固定武器：强制锁定为 0 (永远指向单位正前方)
            this.rotation = 0f;
        }

        // 射击判定 - 提前检查目标有效性
        if (target == null || target.health < 0f || reloadTimer > 0) {
            return;
        }

        boolean canShoot;
        float shootAngle; // 最终的射击绝对角度

        if (rotate) {
            // 判定标准：炮塔自身的旋转角是否对准了理想挂载角
            canShoot = Angles.within(this.rotation, mountAngle, 2f);
            // 射击角度 = 单位朝向 + 90 + 炮塔偏转角
            shootAngle = owner.rotation + 90 + this.rotation;
        } else {
            // 判定标准：单位的绝对朝向是否对准了目标的绝对角度
            // owner.rotation + 90 代表单位正前方的绝对角度
            float unitFacing = owner.rotation + 90;
            canShoot = Angles.within(unitFacing, owner.angleToTarget, type.shootCone);
            // 射击角度 = 单位正前方
            shootAngle = unitFacing;
        }

        if (canShoot) {
            shoot(wx, wy, shootAngle);
        }
    }

    private void shoot(float wx, float wy, float angle) {
        this.reloadTimer = type.reload;

        owner.addHeat(type.heatPerShot);

        // 计算枪口位置 (基于传入的最终射击角度)
        float bulletX = wx + Angles.trnsx(angle, type.shootX, type.shootY);
        float bulletY = wy + Angles.trnsy(angle, type.shootX, type.shootY);

        if (type.bullet != null) {
            // 传递 owner 的速度用于惯性叠加
            Bullet.create(type.bullet, owner, bulletX, bulletY, angle, owner.speedX, owner.speedY);
        }

        // 交替射击同步
        if (!type.isMirror && type.otherSide != -1 && type.alternate) {
            if (type.otherSide < owner.weapons.size) {
                Weapon mirror = owner.weapons.get(type.otherSide);
                if (mirror != null) {
                    mirror.reloadTimer = type.reload / 2f;
                }
            }
        }
    }
}
