package caliniya.vergvoke.ui.fragment;

import arc.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.io.*;
import caliniya.vergvoke.type.enhance.shield.*;
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

							// InitGame.testinit();
							menu.add(
									new Button(
											"@start",
											UI::Game));
							menu.row();

							menu.add(
									new Button(
											"@mapList",
											UI::Maps));
							menu.row();

							menu.add(
									new Button(
											"test2",
											() -> {
												WorldData.initWorld(100, 100, true);
												Data.loadSystems();

												Unit A = UnitTypes.test.create(TeamTypes.Evoke, 100, 100);

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
													WorldData.placeBuilding(Blocks.TestBlock, bx, by, 0, TeamTypes.Mutex);
												}

												// --- 新增：在地图中心生成敌方测试炮塔 ---
												int centerX = WorldData.world.W / 2;
												int centerY = WorldData.world.H / 2;

												Building enemyTurret
														= WorldData.placeBuilding(
														Blocks.testTurret, centerX, centerY, 0, TeamTypes.Mutex);

												// 导航增量更新已由 WorldData.placeBuilding 内部处理，不再手动全量重建
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
												Data.load(
														Core.settings.getDataDirectory().child("map/space.aevs"),
														() -> {
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
