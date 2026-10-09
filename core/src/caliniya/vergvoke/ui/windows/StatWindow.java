package caliniya.vergvoke.ui.windows;

import arc.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.core.meta.stat.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.ability.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.ui.Button;

/**
 * 实体属性窗口：实时运行时数据（血量/护甲/护盾/能量/热量/电力）、能力与模组统计，
 * 带最大值的条目附进度条。单位/建筑通用，标题取实体类型的本地化名。
 */
public class StatWindow extends Window {

	public Entity<?, ?> entity;

	public StatStack stat;

	public StatWindow(Entity<?, ?> entity) {
		super(titleOf(entity));
		this.entity = entity;
		this.stat = new StatStack();
		main.update(this::checkStructure);
	}

	/** 结构版本：能力/模组数量变化时才重建表格（平时每帧只刷新数据）。 */
	private int enhancementCount = -1;

	/** 模组数量变化（罕见）→ 重建表格结构；平时什么都不做。 */
	private void checkStructure() {
		if (entity == null || entity.type == null)
			return;
		if (entity.enhancements.size != enhancementCount) {
			main(main);
		}
	}

	@Override
	public void main(Table t) {
		if (entity == null || entity.type == null)
			return;
		stat.clear();
		t.clearChildren();

		// 组装无分组运行时数据：实体（血量/护甲/护盾/能量/热量/电力）+ 能力 + 模组
		entity.stat(stat);
		for (Enhancement enh : entity.enhancements) {
			enh.type.stats(stat);
		}

		// 渲染：完整遍历所有 StatData（data 已含缩进），跳过空内容
		stat.each(
				d -> {
					if (d.data == null || d.data.trim().isEmpty())
						return;
					if (d.stat != null && d.valueMax > 0f) {
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

		// 可开关模组 + 能力（带开关按钮）
		t.add().height(8f).row();
		for (Enhancement enh : entity.enhancements) {
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
		for (Ability a : entity.abilities) {
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

		enhancementCount = entity.enhancements.size;
	}

	/** 窗口标题：实体类型的本地化名（单位走 ContentType，建筑过渡期借 block 的名字），取不到回退通用名。 */
	static String titleOf(Entity<?, ?> e) {
		return e.type.localizedName;
	}
}
