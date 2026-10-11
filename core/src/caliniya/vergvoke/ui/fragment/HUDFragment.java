package caliniya.vergvoke.ui.fragment;

import arc.*;
import arc.input.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.ui.Button;
import caliniya.vergvoke.ui.windows.*;

public class HUDFragment {

	private Table root;
	private Table rightContainer;


	private Table buildingPanel;

	private Table commandPanel;

	private Table unitInfoTable;

	private Button moveBtn, stopBtn;

	// ===== 建筑面板状态 =====

	/** 按分类分组的可建建筑（build 时构建一次） */
	private ObjectMap<BuildingType.BlockCategory, Ar<BuildingType>> cateBlocks = new ObjectMap<>();

	/** 当前选中的建筑（null = 未选中） */
	private BuildingType selBlock;

	/** 当前悬停的建筑（null = 无悬停，信息区显示选中项） */
	private BuildingType hoverBlock;

	/** 各分类页签按钮（切换分类时取消其它页签高亮） */
	private ObjectMap<BuildingType.BlockCategory, Button> cateBtns = new ObjectMap<>();

	/// 当前展示的建筑图标网格（悬停/选中刷新时重建内容）
	private Table blockGrid;

	/// 顶部信息标签（悬停/选中刷新时更新文本）
	private Label infoLabel;

	/// 当前展示的分类（默认生产）
	private BuildingType.BlockCategory curCate = BuildingType.BlockCategory.production;

	/** 图标格边长 */
	private static final float iconSize = 64f;

	/// 图标网格行列数（固定 4×4 标准，从左上角排列）
	private static final int gridCol = 4, gridRow = 4;

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
						new Button("@菜单",
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

	/** 收集所有可建建筑并按分类分组（build 时调一次）。 */
	private void collectBlocks() {
		cateBlocks.clear();
		for (ContentType c : Contents.getByType(CType.Block)) {
			BuildingType b = (BuildingType) c;
			if (!b.buildable)
				continue;
			Ar<BuildingType> list = cateBlocks.get(b.category);
			if (list == null)
				cateBlocks.put(b.category, list = new Ar<>());
			list.add(b);
		}
	}

	/** 重建建筑面板：顶部信息区 + 中间图标网格 + 底部分类页签。 */
	private void setupBuildingPanel() {
		collectBlocks();

		buildingPanel = new Table();
		buildingPanel.background(Styles.background);

		// 顶部信息区：优先显示悬停项，无悬停显示选中项
		infoLabel = new Label("");
		infoLabel.setWrap(true);
		buildingPanel.add(infoLabel).growX().left().pad(6f).row();
		buildingPanel.image(Styles.divider, Pal.light).height(2f).growX().pad(4f).row();

		// 中间：当前分类的图标网格（可滚动，固定 4×4 高度占位，即使为空也展开）
		blockGrid = new Table();
		buildingPanel.add(new ScrollPane(blockGrid)).grow().minHeight(gridRow * (iconSize + 6f)).row();
		buildingPanel.image(Styles.divider, Pal.light).height(2f).growX().pad(4f).row();

		// 底部：分类页签
		Table cateRow = new Table();
		cateRow.defaults().size(64f, 40f).pad(3f);
		for (BuildingType.BlockCategory cate : BuildingType.BlockCategory.values()) {
			Button btn = new Button(cateName(cate), () -> switchCate(cate));
			cateBtns.put(cate, btn);
			cateRow.add(btn);
		}
		buildingPanel.add(cateRow).growX().left();

		refreshBlockGrid();
		refreshBlockInfo();
	}

	/// 分类显示名（暂用英文名，后续接入本地化）。
	private String cateName(BuildingType.BlockCategory cate) {
		switch (cate) {
			case turret:
				return "防御";
			case production:
				return "生产";
			case unit:
				return "单位";
			case logistics:
				return "后勤";
			default:
				return "其他";
		}
	}

	/** 切换分类页签：清除所选并重建图标网格，高亮当前页签。 */
	private void switchCate(BuildingType.BlockCategory cate) {
		curCate = cate;
		selBlock = null;
		refreshBlockInfo();
		for (Button b : cateBtns.values())
			b.setChecked(b == cateBtns.get(cate));
		refreshBlockGrid();
	}

	/** 重建当前分类的图标网格：4×4 标准从左上角排列，图标用建筑贴图，选中项叠加 select 框（无按钮样式）。 */
	private void refreshBlockGrid() {
		blockGrid.clearChildren();
		blockGrid.top().left();
		Ar<BuildingType> list = cateBlocks.get(curCate, new Ar<>());
		for (int i = 0; i < list.size; i++) {
			BuildingType b = list.get(i);
			// 图标 + 选中框叠放：图标内缩一圈，避免边缘被 select 框遮住
			Stack stack = new Stack();
			Table inner = new Table();
			inner.add(new Image(b.region)).size(iconSize - 10f, iconSize - 10f);
			stack.add(inner);
			Image selImg = new Image(Styles.sel);
			selImg.setScaling(Scaling.stretch);
			selImg.visible = false;
			stack.add(selImg);
			stack.setSize(iconSize, iconSize);
			stack.update(() -> selImg.visible = selBlock == b);
			stack.addListener(new InputListener() {
				@Override
				public void enter(InputEvent event, float x, float y, int pointer, Element from) {
					hoverBlock = b;
					refreshBlockInfo();
				}

				@Override
				public void exit(InputEvent event, float x, float y, int pointer, Element to) {
					if (hoverBlock == b) {
						hoverBlock = null;
						refreshBlockInfo();
					}
				}

				@Override
				public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
					selectBlock(b);
					return true;
				}
			});
			blockGrid.add(stack).size(iconSize, iconSize).pad(3f);
			if ((i + 1) % gridCol == 0)
				blockGrid.row();
		}
	}

	/** 点击图标切换选中（移动端无悬停，直接点击即选中）。 */
	private void selectBlock(BuildingType b) {
		selBlock = (selBlock == b) ? null : b;
		refreshBlockInfo();
	}

	/** 刷新顶部信息区：悬停优先，无悬停显示选中；两者都没有时什么都不显示。 */
	private void refreshBlockInfo() {
		BuildingType show = hoverBlock != null ? hoverBlock : selBlock;
		if (show == null) {
			infoLabel.setText("");
		} else {
			// 目前所有信息都用名字代替，后续接入统计/描述
			infoLabel.setText("[light]" + show.name + "[]"
					+ (show == selBlock ? "  [gray](已选中)[]" : ""));
		}
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
