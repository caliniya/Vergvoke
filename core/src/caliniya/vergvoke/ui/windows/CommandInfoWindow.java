package caliniya.vergvoke.ui.windows;

import arc.Core;
import arc.scene.ui.layout.Table;
import caliniya.vergvoke.base.ecs.EntityArs;
import caliniya.vergvoke.game.Game;
import caliniya.vergvoke.ui.Button;

/**
 * 指挥信息窗口：列出所有友军单位的具体信息。
 *
 * <p>
 * 目前硬编码显示血量/护盾/护甲/能量； 未来由 Entity 上的"展示自身信息"方法提供。
 */
public class CommandInfoWindow extends Window {

    public CommandInfoWindow() {
        super(Core.bundle.get("commandInfo.title"));
    }

    @Override
    public void main(Table t) {
        int[] count = { 0 };
        EntityArs.Unit.each(
                u -> {
                    if (u == null || u.team != Game.team)
                        return;
                    count[0]++;
                    Table row = new Table();
                    row.left();
                    row.add(
                            Core.bundle.format(
                                    "commandInfo.row",
                                    u.type.name,
                                    (int) u.health,
                                    (int) u.totalShield(),
                                    (int) u.armor,
                                    (int) u.energy))
                            .left()
                            .pad(2f);
                    row.add(new Button("@commandInfo.detail", () -> new UnitDetailWindow(u).build()))
                            .size(60f, 36f)
                            .padLeft(6f);
                    t.add(row).growX().left().row();
                });
        if (count[0] == 0) {
            t.add("[gray]" + Core.bundle.get("commandInfo.empty") + "[]").pad(10f);
        }
    }
}
