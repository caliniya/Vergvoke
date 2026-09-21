package caliniya.vergvoke.ui.windows;

import arc.*;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import caliniya.vergvoke.content.UnitTypes;
import caliniya.vergvoke.core.Render;
import caliniya.vergvoke.core.UI;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.type.ability.ShieldAbility;
import caliniya.vergvoke.ui.Button;
import caliniya.vergvoke.base.type.*;

public class PauseWindow extends Window {
    /** 呃啊 */
    public PauseWindow() {
        super("@pauseWindow");
        w = Core.graphics.getWidth() / 2f;
        h = Core.graphics.getHeight() / 2f;
        modal = true;
        showFullButton = false; // 暂停菜单不需要全屏
    }

    @Override
    public void main(Table t) {
        // 单例反复 build()，先清空避免按钮累积
        t.clearChildren();
        t.add(
                new Button(
                        "宇宙",
                        () -> {

                            // 同步宇宙相机到游戏相机位置
                            Render.universeCamera.position.set(Core.camera.position);
                            Render.universeCamera.width = Core.camera.width;
                            Render.universeCamera.height = Core.camera.height;
                            UI.universe.build();
                            UI.hud.hideHUD();
                            this.window.visible = false;
                            this.modalOverlay.visible = false;
                        }))
                .size(120f, 50f)
                .left()
                .top();
        t.row();
        t.add(new Button("科技树", () -> new TechTreeWindow().build())).size(120f, 50f).left().top();
        t.row();
        t.add(new Button("战斗测试", () -> testCombat())).size(120f, 50f).left().top();
    }

    /** 战斗系统测试（验证 Step1/2 的伤害结算），结果打印到日志。 预期值基于：护甲强度 30，满盾 500（最大强度 2），护盾按容量比例减伤。 */
    private void testCombat() {
        Log.info("========== 战斗系统测试开始 ==========");

        if (WorldData.world == null) {
            Log.warn("[测试] 世界未初始化，请先进入一局游戏");
            return;
        }

        Unit u = UnitTypes.test.create(TeamTypes.Evoke, 0, 0);
        u.health = 500;
        u.maxHealth = 500;
        u.armor = 500;
        u.armorMax = 500;
        u.armorValue = 30;
        // 隔离类型默认抗性：公式验证用全 0 抗性基线（第 8 步再手动设置动能抗性）
        u.armorResist = new float[DamageType.values().length];

        ShieldAbility shield = new ShieldAbility(500);
        shield.regen = 0;
        shield.energyCost = 0;
        u.addAbility(shield);

        Log.info("[1] 初始: 盾=@ 血=@ (预期 盾500/血500)", shield.current, u.health);

        // 满盾(比例1.0, 强度2)被能量100打：100×1.5/2 = 75
        u.applyDamage(100f, DamageType.Energy);
        Log.info("[2] 满盾能量100: 盾=@ 血=@ (预期 盾425/血500)", shield.current, u.health);

        // 盾425(比例0.85, 强度1.7)能量100：100×1.5/1.7 ≈ 88.2
        u.applyDamage(100f, DamageType.Energy);
        Log.info("[3] 盾425能量100: 盾=@ 血=@ (预期 盾≈336.8/血500)", shield.current, u.health);

        // 动能1000打穿护盾，溢出不传递 → 盾0，血不变
        u.applyDamage(1000f, DamageType.Kinetic);
        Log.info("[4] 破盾动能1000: 盾=@ 血=@ (预期 盾0/血500, 溢出不传递)", shield.current, u.health);

        // 无盾动能100：100×1.0-30 = 70 → 扣护甲容量，血不变
        u.applyDamage(100f, DamageType.Kinetic);
        Log.info("[5] 无盾动能100: 甲=@ 血=@ (预期 甲430/血500)", u.armor, u.health);

        // 无盾热能100：100×1.5-30 = 120 → 甲 430-120=310
        u.applyDamage(100f, DamageType.Thermal);
        Log.info("[6] 无盾热能100: 甲=@ 血=@ (预期 甲310/血500)", u.armor, u.health);

        // 低于护甲强度的伤害完全挡下
        u.applyDamage(10f, DamageType.Kinetic);
        Log.info("[7] 动能10(<护甲强度30): 甲=@ 血=@ (预期 甲310/血500)", u.armor, u.health);

        // 护甲对动能 50% 抗性：100×1.0×0.5-30 = 20 → 甲 310-20=290
        u.armorResist[DamageType.Kinetic.ordinal()] = 0.5f;
        u.applyDamage(100f, DamageType.Kinetic);
        Log.info("[8] 护甲动能抗性50%: 甲=@ 血=@ (预期 甲290/血500)", u.armor, u.health);

        // 打穿护甲：动能1000 → 甲 290-970<0 → 甲0，剩余不传递 → 血不变
        u.applyDamage(1000f, DamageType.Kinetic);
        Log.info("[9] 破甲动能1000: 甲=@ 血=@ (预期 甲0/血500, 剩余不传递)", u.armor, u.health);

        // 破甲后本体：动能100 → 无护甲减伤 → 血 500-100=400
        u.applyDamage(100f, DamageType.Kinetic);
        Log.info("[10] 破甲后动能100: 血=@ (预期 400)", u.health);

        u.remove();
        Log.info("========== 战斗系统测试结束 ==========");
    }
}
