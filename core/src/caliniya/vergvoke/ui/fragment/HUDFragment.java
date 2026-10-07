package caliniya.vergvoke.ui.fragment;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.ui.windows.*;

public class HUDFragment {

	private Table root;
	private Table rightContainer;
	private Table buildingPanel;
	private Table commandPanel;
	private Table unitInfoTable;
	private Button moveBtn, stopBtn;
	private Element healthBarElement; // 复用的血条元素
	private Element energyBarElement; // 复用的能量条元素
	private Element heatBarElement; // 复用的热量条元素
	private Unit selectedUnit; // 血条当前绑定的单位

	// A左上 B左下
	public Table a, b;

	public void build() {
		root = new Table();
		root.setFillParent(true);
		root.touchable = Touchable.childrenOnly;
		Core.scene.root.addChild(root);

		a = new Table().top().left();
		b = new Table().bottom().left();

		a.add(
						new Button(
								"@菜单",
								() -> {
									UI.pauseWindow.build();
								}))
				.size(120f, 50f);

		// 调试测试窗入口（调试显示器关闭时隐藏）
		if (UI.debugShown()) {
			a.add(new Button("调试", DebugWindow::toggle)).size(80f, 50f).padLeft(6f);
		}

		Button commandBtn = new Button(
				() -> {
					CommandData.commanding = !CommandData.commanding;
					updateRightPanel();
				},
				"@command");

		b.add(commandBtn).size(120f, 50f).margin(10f);

		// === 右下角：动态面板容器 ===""
		rightContainer = new Table();
		rightContainer.bottom().right();

		setupBuildingPanel();
		setupCommandPanel();

		updateRightPanel();

		root.add(a).top().left();
		root.row();
		root.row();
		root.add(b).bottom().left();
		root.add(rightContainer).expand().bottom().right();
	}

	/** 隐藏游戏 HUD（切换至宇宙视图时） */
	public void hideHUD() {
		if (root != null) {
			root.visible = false;
			root.touchable = Touchable.disabled;
		}
	}

	/** 恢复游戏 HUD（返回地图视图时） */
	public void showHUD() {
		if (root != null) {
			root.visible = true;
			root.touchable = Touchable.childrenOnly;
		}
	}

	private void setupBuildingPanel() {
		buildingPanel = new Table();
		buildingPanel.background(Styles.background);

		buildingPanel.add("[lightgray]建筑菜单[]").row();
		buildingPanel.add().height(10f).row();

		Table btnRow = new Table();
		btnRow.defaults().size(70f, 50f).pad(5f);

		btnRow.button("@aa", () -> Log.info("打开电力建筑列表"));
		btnRow.button("生产", () -> Log.info("打开生产建筑列表"));
		btnRow.button("防御", () -> Log.info("打开防御建筑列表"));

		buildingPanel.add(btnRow);
	}

	private void setupCommandPanel() {
		commandPanel = new Table();
		commandPanel.background(Styles.background);

		// 顶部：指挥信息 + 清空选择
		Table topRow = new Table();
		topRow.defaults().size(90f, 40f).pad(2f);
		topRow.left().top();
		topRow.add(new Button("指挥信息", () -> new CommandInfoWindow().build()));
		topRow.add(new Button("清空", () -> clearSelection()));
		commandPanel.add(topRow).growX().left();
		commandPanel.row();
		commandPanel.add().height(6f).row();

		// 单位信息区（动态刷新）
		unitInfoTable = new Table();
		commandPanel.add(unitInfoTable).growX();
		commandPanel.row();
		commandPanel.add().height(6f).row();

		// 直接指挥行（单选，按下高亮）
		Table directRow = new Table();
		directRow.defaults().size(85f, 44f).pad(3f);
		moveBtn = new Button("移动", () -> setCommand(CommandData.CommandType.Move));
		stopBtn = new Button("停止", () -> setCommand(CommandData.CommandType.Stop));
		moveBtn.setChecked(true); // 默认移动模式
		directRow.left().bottom();
		directRow.add(moveBtn);
		directRow.add(stopBtn);
		commandPanel.add(directRow).growX().left();
		commandPanel.row();
		commandPanel.add().height(6f).row();

		// 单位状态行：占位 + 批量开关可切换能力
		Table stateRow = new Table();
		stateRow.defaults().size(70f, 40f).pad(3f);
		stateRow.left().bottom();
		stateRow.button("待命", () -> Log.info("指令：原地待命（未实现）"));
		stateRow.button("停火", () -> Log.info("指令：停火（未实现）"));
		stateRow.button("全部开启", () -> toggleAllAbilities(true));
		stateRow.button("全部关闭", () -> toggleAllAbilities(false));
		commandPanel.add(stateRow).growX().left();

		refreshCommand();
	}

	/** 清空当前选中的单位列表。 */
	public void clearSelection() {
		for (Unit u : CommandData.checkedUnits) {
			if (u != null)
				u.isSelected = false;
		}
		CommandData.checkedUnits.clear();
		CommandData.commandType = CommandData.CommandType.Move; // 恢复默认移动模式
		moveBtn.setChecked(true);
		stopBtn.setChecked(false);
		refreshCommand();
	}

	/** 批量开关所有选中单位的可切换能力。 */
	private void toggleAllAbilities(boolean enabled) {
		for (Unit u : CommandData.checkedUnits) {
			if (u != null)
				u.setAllAbilities(enabled);
		}
		refreshCommand();
	}

	/** 切换直接指挥状态（单选：点中高亮，再点取消，切换时其他自动关）。 */
	private void setCommand(CommandData.CommandType type) {
		CommandData.commandType = (CommandData.commandType == type) ? CommandData.CommandType.None : type;
		moveBtn.setChecked(CommandData.commandType == CommandData.CommandType.Move);
		stopBtn.setChecked(CommandData.commandType == CommandData.CommandType.Stop);
		refreshCommand();
	}

	/** 刷新指挥面板：选中单位信息 + 当前指令状态。 */
	public void refreshCommand() {
		if (unitInfoTable == null)
			return;

		// 清理死亡/失效单位（null 或血量归零）并取消选中
		for (int i = CommandData.checkedUnits.size - 1; i >= 0; i--) {
			Unit u = CommandData.checkedUnits.get(i);
			if (u == null || u.health <= 0) {
				CommandData.checkedUnits.remove(i);
				if (u != null)
					u.isSelected = false;
			}
		}

		unitInfoTable.clearChildren();

		if (CommandData.checkedUnits.isEmpty()) {
			selectedUnit = null;
			unitInfoTable.add("[gray]未选择单位[]").left().pad(2f);
		} else if (CommandData.checkedUnits.size == 1) {
			Unit u = CommandData.checkedUnits.first();
			selectedUnit = u;
			Table infoRow = new Table();
			infoRow.left();
			infoRow.add("[light]" + u.type.name + "[]").left().pad(2f);
			infoRow
					.add(new Button("详情", () -> new UnitDetailWindow(u).build()))
					.size(64f, 36f)
					.padLeft(8f);
			unitInfoTable.add(infoRow).growX().left().row();
			// 血条（占满）+ 能量条（紧贴下方，长度一致）
			unitInfoTable.add(healthBar()).growX().minWidth(140f).height(10f).left().row();
			if (u.energyMax > 0f) {
				unitInfoTable.add(energyBar()).growX().height(10f).left().padTop(0f).row();
			}
			if (u.heatable && u.heatMax > 0f) {
				unitInfoTable.add(heatBar()).growX().height(10f).left().padTop(2f).row();
			}
		} else {
			selectedUnit = null;
			for (Unit u : CommandData.checkedUnits) {
				unitInfoTable.add("[light]" + u.type.name + "[]").left().pad(1f).row();
			}
		}

		// 当前指令状态提示
		unitInfoTable.row();
		String cmdText = CommandData.commandType == CommandData.CommandType.Move
				? "[sky]移动指令中：点地图目标[]"
				: CommandData.commandType == CommandData.CommandType.Stop
				? "[sky]停止指令中：点击执行[]"
				: "[gray]无指令[]";
		unitInfoTable.add(cmdText).left().padTop(4f);
	}

	/** 整合血条元素（核心/护甲/护盾三段 Bar,段宽按上限占比分配;复用实例,绘制时读取 selectedUnit）。 */
	private Element healthBar() {
		if (healthBarElement == null) {
			Table t = new Table();
			Bar coreBar = new Bar(() -> {
				Unit u = selectedUnit;
				return u == null || u.maxHealth <= 0f ? 0f : Mathf.clamp(u.health / u.maxHealth);
			}, Color.scarlet);
			Bar armorBar = new Bar(() -> {
				Unit u = selectedUnit;
				return u == null || u.armorMax <= 0f ? 0f : Mathf.clamp(u.armor / u.armorMax);
			}, Color.lightGray);
			Bar shieldBar = new Bar(() -> {
				Unit u = selectedUnit;
				return u == null || u.totalShieldMax() <= 0f
						? 0f
						: Mathf.clamp(u.totalShield() / u.totalShieldMax());
			}, Color.sky);

			Cell<Bar> cc = t.add(coreBar).height(10f);
			Cell<Bar> ac = t.add(armorBar).height(10f);
			Cell<Bar> sc = t.add(shieldBar).height(10f);

			// 段宽按三者上限占比分配,总宽跟随外层布局的实际宽度(growX 拉伸),上限变化时随帧刷新
			// 注意:Cell.width() 只改字段不触发重排,必须手动 invalidate,否则内层 Bar 停在初始 0 宽
			t.update(() -> {
				Unit u = selectedUnit;
				float coreMax = u == null ? 0f : Math.max(0f, u.maxHealth);
				float armorMax = u == null ? 0f : Math.max(0f, u.armorMax);
				float shieldMax = u == null ? 0f : Math.max(0f, u.totalShieldMax());
				float total = coreMax + armorMax + shieldMax;
				float w = t.getWidth();
				if (total <= 0f || w <= 0f) {
					cc.width(0f);
					ac.width(0f);
					sc.width(0f);
				} else {
					cc.width(w * coreMax / total);
					ac.width(w * armorMax / total);
					sc.width(w * shieldMax / total);
				}
				t.invalidate();
			});

			healthBarElement = t;
		}
		return healthBarElement;
	}

	/** 整合能量条元素（复用一个实例，绘制时读取 selectedUnit）。 */
	private Element energyBar() {
		if (energyBarElement == null) {
			Bar bar = new Bar(() -> {
				Unit u = selectedUnit;
				return u == null || u.energyMax <= 0f ? 0f : Mathf.clamp(u.energy / u.energyMax);
			}, Pal.light);
			bar.setSize(10f, 10f);
			energyBarElement = bar;
		}
		return energyBarElement;
	}

	/** 整合热量条元素（复用一个实例，绘制时读取 selectedUnit;不可产热的单位画空槽）。 */
	private Element heatBar() {
		if (heatBarElement == null) {
			Bar bar = new Bar(() -> {
				Unit u = selectedUnit;
				return u == null || !u.heatable || u.heatMax <= 0f
						? 0f
						: Mathf.clamp(u.heat / u.heatMax);
			}, Color.orange);
			bar.setSize(10f, 10f);
			heatBarElement = bar;
		}
		return heatBarElement;
	}

	private void updateRightPanel() {
		rightContainer.clearChildren();
		Table currentPanel = CommandData.commanding ? commandPanel : buildingPanel;
		currentPanel.clearActions();
		Cell<Table> cell = rightContainer.add(currentPanel).bottom();

		float minW = Core.scene.getWidth() / 5f;
		cell.minWidth(minW);

		float height = currentPanel.getPrefHeight();
		currentPanel.setTranslation(0, -height);
		currentPanel.addAction(Actions.translateBy(0, height, 0.3f, Interp.fade));
	}
}
