package caliniya.vergvoke.ui.fragment;

import arc.Core;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import caliniya.vergvoke.core.meta.ui.Pal;
import caliniya.vergvoke.base.ecs.Unit;
import arc.util.Log;
import caliniya.vergvoke.core.UI;
import caliniya.vergvoke.game.data.CommandData;
import caliniya.vergvoke.ui.Button;
import caliniya.vergvoke.ui.Styles;
import caliniya.vergvoke.ui.windows.CommandInfoWindow;
import caliniya.vergvoke.ui.windows.UnitDetailWindow;

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
            unitInfoTable.add(healthBar()).growX().height(10f).left().row();
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

    /** 整合血条元素（复用一个实例，绘制时读取 selectedUnit）。 */
    private Element healthBar() {
        if (healthBarElement == null) {
            healthBarElement = new Element() {
                {
                    setSize(140f, 10f);
                }

                @Override
                public void draw() {
                    if (selectedUnit == null)
                        return;
                    float x = this.x;
                    float y = this.y;
                    float w = getWidth();
                    float h = getHeight();

                    Unit u = selectedUnit;
                    float core = Math.max(0f, u.health);
                    float coreMax = Math.max(0f, u.maxHealth);
                    float armor = Math.max(0f, u.armor);
                    float armorMax = Math.max(0f, u.armorMax);
                    float shield = Math.max(0f, u.totalShield());
                    float shieldMax = Math.max(0f, u.totalShieldMax());

                    float totalMax = coreMax + armorMax + shieldMax;
                    if (totalMax <= 0f)
                        return;

                    // 底色
                    Draw.color(Color.darkGray);
                    Fill.rect(x + w / 2f, y + h / 2f, w, h);

                    // 核心段（红，最左）
                    float coreW = w * coreMax / totalMax;
                    if (coreW > 0f && core > 0f) {
                        float fw = coreW * (core / coreMax);
                        Draw.color(Color.scarlet);
                        Fill.rect(x + fw / 2f, y + h / 2f, fw, h);
                    }

                    // 护甲段（白，中）
                    float armorW = w * armorMax / totalMax;
                    if (armorW > 0f && armor > 0f) {
                        float fw = armorW * (armor / armorMax);
                        Draw.color(Color.lightGray);
                        Fill.rect(x + coreW + fw / 2f, y + h / 2f, fw, h);
                    }

                    // 护盾段（蓝，右）
                    float shieldW = w * shieldMax / totalMax;
                    if (shieldW > 0f && shield > 0f) {
                        float fw = shieldW * (shield / shieldMax);
                        Draw.color(Color.sky);
                        Fill.rect(x + coreW + armorW + fw / 2f, y + h / 2f, fw, h);
                    }

                    Draw.color();
                }
            };
        }
        return healthBarElement;
    }

    /** 整合能量条元素（复用一个实例，绘制时读取 selectedUnit）。 */
    private Element energyBar() {
        if (energyBarElement == null) {
            energyBarElement = new Element() {
                {
                    setSize(10f, 10f);
                }

                @Override
                public void draw() {
                    if (selectedUnit == null || selectedUnit.energyMax <= 0f)
                        return;
                    float x = this.x;
                    float y = this.y;
                    float w = getWidth();
                    float h = getHeight();

                    Unit u = selectedUnit;
                    float ratio = Math.min(1f, Math.max(0f, u.energy) / u.energyMax);

                    // 底色（空条槽位）
                    Draw.color(Color.darkGray);
                    Fill.rect(x + w / 2f, y + h / 2f, w, h);
                    if (ratio > 0f) {
                        float fw = w * ratio;
                        Draw.color(Pal.light);
                        Fill.rect(x + fw / 2f, (y + h / 2f), fw, h);
                    }

                    Draw.color();
                }
            };
        }
        return energyBarElement;
    }

    /** 整合热量条元素（复用一个实例，绘制时读取 selectedUnit）。 */
    private Element heatBar() {
        if (heatBarElement == null) {
            heatBarElement = new Element() {
                {
                    setSize(10f, 10f);
                }

                @Override
                public void draw() {
                    if (selectedUnit == null || !selectedUnit.heatable || selectedUnit.heatMax <= 0f)
                        return;
                    float x = this.x;
                    float y = this.y;
                    float w = getWidth();
                    float h = getHeight();

                    Unit u = selectedUnit;
                    float ratio = Math.min(1f, Math.max(0f, u.heat) / u.heatMax);

                    // 底色（空条槽位）
                    Draw.color(Color.darkGray);
                    Fill.rect(x + w / 2f, y + h / 2f, w, h);
                    // 橙色填充，从左往右（过热时满条）
                    if (ratio > 0f) {
                        float fw = w * ratio;
                        Draw.color(Color.orange);
                        Fill.rect(x + fw / 2f, y + h / 2f, fw, h);
                    }

                    Draw.color();
                }
            };
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
