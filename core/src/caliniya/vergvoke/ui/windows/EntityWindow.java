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
 *
 */
public class EntityWindow extends Window {

	private static EntityWindow current;

	/** 打开实体面板（已有面板先关掉再开新的，防窗口堆积）。 */
	public static void open(Entity<?, ?> e) {
		if (current != null) {
			current.remove();
		}
		current = new EntityWindow(e);
		current.build();
	}

	public Entity<?, ?> entity;

	public StatStack stat;

	public EntityWindow(Entity<?, ?> entity) {
		super(entity.type.localizedName);
		this.entity = entity;
		this.stat = new StatStack();
		main.update(this::checkStructure);
	}

	private int enhancementCount = -1;

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
		entity.stat(stat);
		for (Enhancement enh : entity.enhancements) {
			enh.type.stats(stat);
		}

		stat.each(
				d -> {
					if (d.data == null || d.data.trim().isEmpty())
						return;
					if (d.stat != null && d.valueMax > 0f) {
						Table row = new Table();
						row.left();
						Label l = new Label(d::getData);
						row.add(l).left();
						row.add(new Bar(() -> d.value / d.valueMax, Pal.light)).size(250f, 40f).padLeft(8f);
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
											? Core.bundle.get("disable")
											: Core.bundle.get("enable"),
									() -> enh.setEnabled(!enh.enabled))
									.set(
											b -> b.text.setText(
													() -> Core.bundle.get(
															enh.enabled
																	? "disable"
																	: "enable"))))
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
											? Core.bundle.get("disable")
											: Core.bundle.get("enable"),
									() -> a.setEnabled(!a.enabled))
									.set(
											b -> b.text.setText(
													() -> Core.bundle.get(
															a.enabled
																	? "disable"
																	: "enable"))))
					.size(64f, 36f)
					.padLeft(6f);
			t.add(row).growX().left().row();
		}

		enhancementCount = entity.enhancements.size;
	}

}
