package caliniya.vergvoke.ui.windows;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.type.*;

import java.util.function.*;

/**
 * 建筑信息面板：点击建筑弹出，展示血量、物品库存（图标 + 数量/容量）、生产进度（有配方的建筑）。
 *
 * <p>数值每帧读实体最新值（lambda Label / 自绘 bar）；库存物品种类变化时才重建表格结构。
 * 通过 {@link #open(Building)} 打开——同建筑重复点击换内容重建，避免窗口堆积。
 */
public class BuildingDetailWindow extends Window {

	private static BuildingDetailWindow current;

	/** 打开建筑面板（已有面板先关掉再开新的）。 */
	public static void open(Building b) {
		if (current != null) {
			current.remove();
		}
		current = new BuildingDetailWindow(b);
		current.build();
	}

	private final Building b;

	/** 库存结构指纹：物品位图变化（新物品出现/清空）才重建表格。 */
	private long itemFingerprint = -1;

	public BuildingDetailWindow(Building b) {
		super(b.type.block.localizedName);
		this.b = b;
		main = new Table();
		// 结构检查放在 act 阶段（每帧渲染前），重建表格不会发生在绘制过程中
		main.update(this::checkStructure);
	}

	private void checkStructure() {
		long fp = fingerprint();
		if (fp != itemFingerprint) {
			main(main);
		}
	}

	private long fingerprint() {
		long fp = 0;
		if (b.item != null) {
			int[] items = b.item.items;
			for (int i = 0; i < items.length && i < 64; i++) {
				if (items[i] > 0) {
					fp |= 1L << i;
				}
			}
		}
		return fp;
	}

	@Override
	public void main(Table t) {
		t.clearChildren();

		// --- 血量 ---
		Table hp = new Table();
		hp.left();
		hp.add(new Label(() -> "[light]耐久[] " + (int) b.health + "/" + (int) b.maxHealth)).left();
		hp.add(bar(() -> b.health, () -> b.maxHealth)).padLeft(8f);
		t.add(hp).growX().left().row();

		// --- 库存（无仓库建筑跳过） ---
		// ItemModule.items 按内容 ID 索引（ID 从 1 起），查类型必须走 getByID——
		// Contents.items 是按列表序的另一套数组，直接下标会错位
		if (b.item != null) {
			t.add().height(6f).row();
			boolean any = false;
			int[] items = b.item.items;
			for (int id = 1; id < items.length; id++) {
				int amount = items[id];
				if (amount <= 0) {
					continue;
				}
				ItemType type = Contents.getByID(CType.Item, id);
				if (type == null) {
					continue;
				}
				any = true;
				Table row = new Table();
				row.left();
				row.add(itemDisplay(type, b.item.capacity)).left();
				t.add(row).growX().left().padBottom(2f).row();
			}
			if (!any) {
				t.add("[gray]库存为空[]").left().padBottom(2f).row();
			}
		}

		// --- 生产进度（有配方的建筑） ---
		if (b.outputItem != null) {
			t.add().height(6f).row();
			Table pr = new Table();
			pr.left();
			pr.add(new Label(
							() -> "[light]生产[] " + b.outputItem.localizedName + "  "
									+ (int) (Mathf.clamp(b.progress / b.craftTime) * 100f) + "%"))
					.left();
			pr.add(bar(() -> b.progress, () -> b.craftTime)).padLeft(8f);
			t.add(pr).growX().left().row();
		}

		itemFingerprint = fingerprint();
	}

	/** 物品图标 + 数量/容量（Label 每帧读实体仓库，数量增减不用重建）。 */
	private Element itemDisplay(ItemType type, int capacity) {
		return new Table() {
			{
				add(new Image(type.icon)).size(24f).left();
				add(new Label(() -> b.item.items[type.id] + "/" + capacity)).left().padLeft(4f);
			}
		};
	}

	/** 数值进度条：每帧从 supplier 取 value/max 绘制（血量、生产进度共用）。 */
	private Element bar(Supplier<Float> value, Supplier<Float> max) {
		return new Element() {
			{
				setSize(120f, 6f);
			}

			@Override
			public void draw() {
				float w = getWidth();
				float h = getHeight();

				Draw.color(Color.darkGray);
				Fill.rect(x + w / 2f, y + h / 2f, w, h);
				float m = max.get();
				if (m > 0f) {
					float fw = w * Mathf.clamp(value.get() / m);
					Draw.color(Pal.light);
					Fill.rect(x + fw / 2f, y + h / 2f, fw, h);
				}
				Draw.color();
			}
		};
	}
}
