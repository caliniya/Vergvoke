package caliniya.vergvoke.ui.fragment;

import arc.*;
import arc.math.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.type.type.BuildingType.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.ui.Button;
import caliniya.vergvoke.ui.layout.*;
import caliniya.vergvoke.ui.windows.*;

public class HUDFragment {

	private Table root;
	private Table rightContainer;
	private Table buildingPanel;
	private Table commandPanel;
	private Table unitInfoTable;
	private MeshLayout blockList;
	private Table infoTable;
	private Button moveBtn, stopBtn;

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
								() -> UI.pauseWindow.build()))
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

		// 最上：选中/悬停建筑的信息行（图标 + 名字 + 消耗占位）
		infoTable = new Table();
		buildingPanel.add(infoTable).growX().left().pad(6f).row();
		buildingPanel.add().height(4f).row();
		buildingPanel.image().color(Pal.light).fillX().height(2);
		buildingPanel.row();
		buildingPanel.add().height(4f).row();

		// 中：当前类别的建筑网格（可滚动）
		blockList = new MeshLayout();
		blockList.defaults().size(56f, 56f).pad(3f);
		blockList.onHover = this::refreshBlockInfo;
		blockList.onSelect = this::refreshBlockInfo;
		ScrollPane pane = new ScrollPane(blockList);
		pane.setFadeScrollBars(true);
		buildingPanel.add(pane).growX().maxHeight(200f).pad(6f).row();
		buildingPanel.add().height(6f).row();
		buildingPanel.image().color(Pal.light).fillX().height(2);
		buildingPanel.row();
		buildingPanel.add().height(4f).row();

		// 下：类别行（点击切换上方列表）
		Table catRow = new Table();
		catRow.defaults().size(70f, 44f).pad(3f);
		catRow.left().bottom();
		BlockCategory[] cats = BlockCategory.values();
		Button[] catBtns = new Button[cats.length];
		for (int i = 0; i < cats.length; i++) {
			BlockCategory cat = cats[i];
			Button btn = new Button(catName(cat), () -> showCategory(cat, catBtns));
			btn.userObject = cat;
			catBtns[i] = btn;
			catRow.add(btn);
		}
		buildingPanel.add(catRow).growX().left();

		showCategory(BlockCategory.production, catBtns);
	}

	/** 信息行内容：悬停优先，其次选中，都没有 = 提示文字。 */
	private void refreshBlockInfo() {
		infoTable.clearChildren();
		BuildingType bt = null;
		if (blockList.hoverItem != null)
			bt = (BuildingType) blockList.hoverItem.userObject;
		if (bt == null && blockList.selItem != null)
			bt = (BuildingType) blockList.selItem.userObject;

		if (bt == null) {
			infoTable.add("[gray]悬停预览，左键选择[]").left().pad(2f);
			return;
		}

		boolean selected = blockList.selItem != null && blockList.selItem.userObject == bt;
		infoTable.add(new arc.scene.ui.Image(bt.region)).size(40f).left();
		Table text = new Table();
		text.add((selected ? "[light]" : "") + bt.localizedName + (selected ? "[]" : "")).left().row();
		text.add("[gray]消耗：（未实现）[]").left();
		infoTable.add(text).growX().left().padLeft(6f);
	}

	/** 刷新建筑网格：显示指定类别下所有可建造建筑，选中类别按钮高亮。 */
	private void showCategory(BlockCategory cat, Button[] catBtns) {
		for (Button catBtn : catBtns) {
			catBtn.setChecked(catBtn.userObject == cat);
		}

		blockList.clearChildren();
		blockList.defaults().size(56f, 56f).pad(3f);
		boolean any = false;
		for (ContentType c : Contents.getByType(CType.Block)) {
			if (c instanceof BuildingType bt && bt.buildable && bt.category == cat) {
				any = true;
				MeshLayout.MeshItem item = blockList.item(bt.region, () -> Log.info("选中建筑：" + bt.name + "（建造逻辑未接入）"));
				item.userObject = bt;
			}
		}
		if (!any) {
			blockList.add("[gray]该类别暂无建筑[]").left().pad(6f);
		}
		refreshBlockInfo();
	}

	/** 类别显示名（占位直写，以后挪 bundle）。 */
	private static String catName(BlockCategory cat) {
		return switch (cat) {
			case turret -> "炮塔";
			case production -> "生产";
			case unit -> "单位";
			case logistics -> "物流";
			case misc -> "其他";
		};
	}

	private void setupCommandPanel() {
		commandPanel = new Table();
		commandPanel.background(Styles.background);

		// 顶部：指挥信息 + 清空选择
		Table topRow = new Table();
		topRow.defaults().size(90f, 40f).pad(2f);
		topRow.left().top();
		topRow.add(new Button("指挥信息", () -> new CommandInfoWindow().build()));
		topRow.add(new Button("清空", this::clearSelection));
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
			unitInfoTable.add("[gray]未选择单位[]").left().pad(2f);
		} else if (CommandData.checkedUnits.size == 1) {
			Unit u = CommandData.checkedUnits.first();
			Table infoRow = new Table();
			infoRow.left();
			infoRow.add("[light]" + u.type.name + "[]").left().pad(2f);
			infoRow
					.add(new Button("详情", () -> UI.openEntityWindow(u)))
					.size(64f, 36f)
					.padLeft(8f);
			unitInfoTable.add(infoRow).growX().left().row();

		} else {
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
