package caliniya.vergvoke.ui.windows;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.core.meta.stat.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.ability.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.ui.Button;

/**
 * 单位详细信息窗口： 类型信息（名字 + DataWindow 按钮 + stats meta）、 血量/能量/护盾（数字 +
 * 条形图，实时更新）、能力列表（可开关能力带开关按钮）。
 */
public class UnitDetailWindow extends Window {

	private final Unit unit;

	public StatStack stat;

	public UnitDetailWindow(Unit unit) {
		super(unit.type.localizedName);
		this.unit = unit;
		this.stat = new StatStack();
		main = new Table() {
			@Override
			public void draw() {
				super.draw();
			}
		};
		// 结构检查放在 act 阶段（每帧渲染前），重建表格不会发生在绘制过程中
		main.update(this::checkStructure);
	}

	/** 结构版本：能力/模组数量变化时才重建表格（平时每帧只刷新数据）。 */
	private int abilityCount = -1, enhancementCount = -1;

	/** 能力/模组数量变化（罕见）→ 重建表格结构；平时什么都不做。 */
	private void checkStructure() {
		if (unit == null)
			return;
		if (unit.abilities.size != abilityCount || unit.enhancements.size != enhancementCount) {
			main(main);
		}
	}

	@Override
	public void main(Table t) {
		if (unit == null)
			return;
		stat.clear();
		t.clearChildren();

		// 名字 + "类型信息"按钮（复用 DataWindow 展示 StatStack）
		Table nameRow = new Table();
		nameRow.left();
		nameRow.add("[light]" + unit.type.name + "[]").left().pad(2f);
		nameRow
				.add(
						new Button(
								Core.bundle.get("unitDetail.info"), () -> new DataWindow(unit.type.stat).build()))
				.size(84f, 36f)
				.padLeft(8f);
		t.add(nameRow).growX().left().row();

		// 组装无分组运行时数据：实体（血量/护甲/护盾/能量/热量/电力）+ 能力 + 模组
		unit.stat(stat);
		for (Enhancement enh : unit.enhancements) {
			enh.type.stats(stat);
		}

		// 渲染：完整遍历所有 StatData（data 已含缩进），跳过空内容
		stat.each(
				d -> {
					if (d.data == null || d.data.trim().isEmpty())
						return;
					if (d.stat != null && d.valueMax > 0f) {
						// 带最大值的运行时数值行：文本（值 / 最大 (百分比)）+ 进度条
						// 文本格定宽 + ellipsis:数值位数变化不会把条顶得左右晃,超宽文本截断也不压条
						Table row = new Table();
						row.left();
						Label l = new Label(d::getData);
						l.setEllipsis(true);
						row.add(l).left().width(160f);
						row.add(new Bar(() -> d.value / d.valueMax, Pal.light)).size(100f, 6f).padLeft(8f);
						t.add(row).left().padBottom(2f);
					} else {
						t.add(new Label(d::getData)).left().padBottom(2).align(Align.left);
					}
					t.row();
				});

		// 物品数据显示区（TODO：由开发者补充，展示 unit.item / unit.liquid 各资源量）
		// TODO 物品数据

		// 可开关模组 + 能力（带开关按钮）
		t.add().height(8f).row();
		for (Enhancement enh : unit.enhancements) {
			Table row = new Table();
			row.left();
			row.add("[gray]" + enh.type.localizedName + "[]").left().pad(2f);
			row.add(
							new Button(
									enh.enabled
											? Core.bundle.get("unitDetail.disable")
											: Core.bundle.get("unitDetail.enable"),
									() -> enh.setEnabled(!enh.enabled))
									.set(
											b -> b.text.setText(
													() -> Core.bundle.get(
															enh.enabled
																	? "unitDetail.disable"
																	: "unitDetail.enable"))))
					.size(64f, 36f)
					.padLeft(6f);
			t.add(row).growX().left().row();
		}
		for (Ability a : unit.abilities) {
			if (!a.toggleable)
				continue;
			Table row = new Table();
			row.left();
			row.add(a.localizedName).left().pad(2f);
			row.add(
							new Button(
									a.enabled
											? Core.bundle.get("unitDetail.disable")
											: Core.bundle.get("unitDetail.enable"),
									() -> a.setEnabled(!a.enabled))
									.set(
											b -> b.text.setText(
													() -> Core.bundle.get(
															a.enabled
																	? "unitDetail.disable"
																	: "unitDetail.enable"))))
					.size(64f, 36f)
					.padLeft(6f);
			t.add(row).growX().left().row();
		}

		// 记录结构版本，供 refresh 判断是否需要重建
		abilityCount = unit.abilities.size;
		enhancementCount = unit.enhancements.size;
	}
}
