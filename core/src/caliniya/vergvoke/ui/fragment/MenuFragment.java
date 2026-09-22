package caliniya.vergvoke.ui.fragment;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.StringMap;
import arc.util.Log;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.game.data.RouteData;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.io.DataIO;
import caliniya.vergvoke.system.world.*;
import caliniya.vergvoke.type.enhance.shield.ShieldBoostEnhancementType;
import caliniya.vergvoke.ui.*;

public class MenuFragment {

    public Table root;

    public static String temp;

    @SuppressWarnings("unused")
    public void build() {
        root = new Table();
        root.setFillParent(true);
        root.background(null);
        Core.scene.root.addChild(root);

        float menuWidth = 260f;

        root.bottom().left();

        root.table(
                menu -> {
                    menu.defaults().width(menuWidth).height(70f).padBottom(0);

                    menu.add(
                            new Button(
                                    "@start",
                                    () -> {
                                        // InitGame.testinit();
                                        UI.Game();
                                    }));
                    menu.row();

                    menu.add(
                            new Button(
                                    "@mapList",
                                    () -> {
                                        UI.Maps();
                                    }));
                    menu.row();

                    menu.add(
                            new Button(
                                    "test2",
                                    () -> {
                                        WorldData.initWorld(100, 100, true);
                                        Data.loadSystems();
                                        EntityProces.it.init();

                                        // 两个测试单位，各自运行时安装强化模组（出厂无模组，由外部"安装"）
                                        Unit A = UnitTypes.test.create( TeamTypes.Evoke, 100,100);
                                        A.addEnhancement(Enhancements.shieldBoost.create());

                                        Unit B = UnitTypes.test.create(TeamTypes.Mutex, 400, 100);
                                        ShieldBoostEnhancementType b1t = new ShieldBoostEnhancementType(false);
                                        b1t.maxStrengthBonus = 2f;
                                        b1t.kineticResistBonus = 0.1f;
                                        B.addEnhancement(b1t.create());
                                        ShieldBoostEnhancementType b2t = new ShieldBoostEnhancementType(false);
                                        b2t.maxStrengthBonus = 0.5f;
                                        b2t.kineticResistBonus = 0.4f;
                                        B.addEnhancement(b2t.create());

                                        // 生成随机测试建筑
                                        int padding = 5;
                                        int buildingCount = 10;
                                        for (int i = 0; i < buildingCount; i++) {
                                            int bx = padding
                                                    + (int) (Math.random() * (WorldData.world.W - padding * 2));
                                            int by = padding
                                                    + (int) (Math.random() * (WorldData.world.H - padding * 2));
                                            if (WorldData.world.isSolid(bx, by)) {
                                                i--;
                                                continue;
                                            }
                                            WorldData.world.setBuilding(bx, by, Blocks.TestBlock, TeamTypes.Mutex);
                                        }

                                        // --- 新增：在地图中心生成敌方测试炮塔 ---
                                        int centerX = WorldData.world.W / 2;
                                        int centerY = WorldData.world.H / 2;

                                        Building enemyTurret = WorldData.world.setBuilding(
                                                centerX, centerY, Blocks.testTurret, TeamTypes.Mutex);

                                        RouteData.init();
                                        ObjectMap<String, String> tag = new ObjectMap<String, String>();
                                        tag.put("author", "calinya");
                                        tag.put("name", "spaceTest");
                                        tag.put("map", "0000");
                                        DataIO.setSave(
                                                Core.settings.getDataDirectory().child("map/space.aevs"),
                                                new StringMap(tag));
                                        // UI.Game();
                                    }));
                    menu.row();

                    menu.add(
                            new Button(
                                    "test3",
                                    () -> {
                                        Game.team = TeamTypes.Evoke;

                                        // 保留原读档；加载完成后再放置实弹测试场景
                                        Data.load(
                                                Core.settings.getDataDirectory().child("map/space.aevs"),
                                                () -> {
                                                    Log.info("[读档测试] 读档完成，查看上方 [单位创建] 日志确认能力与模组恢复");
                                                });
                                    }));
                    menu.row();

                    menu.add(new Button("@exit", () -> Core.app.exit()));
                })
                .width(menuWidth)
                .padLeft(20f)
                .padBottom(60f);
    }
}
