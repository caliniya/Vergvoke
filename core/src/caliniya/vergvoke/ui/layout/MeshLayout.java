package caliniya.vergvoke.ui.layout;

import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import caliniya.vergvoke.ui.*;

/**
 *
 */
public class MeshLayout extends Table {

	/** 当前悬停项（桌面），null = 无 */
	public MeshItem hoverItem;

	/** 当前选中项，null = 无 */
	public MeshItem selItem;

	/** 悬停变化回调（参数 = 新悬停项，可能为 null） */
	public Runnable onHover;

	/** 选中变化回调（参数 = 新选中项，可能为 null） */
	public Runnable onSelect;

	/** 网格默认格子设置：尺寸/间距/每行列数由外部通过 defaults 控制。 */
	public MeshLayout() {
		touchable = Touchable.childrenOnly;
	}

	/** 清空全部格子并复位状态。 */
	@Override
	public void clearChildren() {
		super.clearChildren();
		hoverItem = null;
		selItem = null;
	}

	/** 新增一个格子。 */
	public MeshItem item(TextureRegion icon, Runnable clicked) {
		MeshItem item = new MeshItem(icon, clicked);
		add(item);
		return item;
	}

	/** 设置选中项（程序化，如取消选择）。 */
	public void select(MeshItem item) {
		if (selItem == item)
			return;
		MeshItem old = selItem;
		selItem = item;
		if (old != null)
			if (item != null)
				if (onSelect != null)
					onSelect.run();
	}

	/** 单个格子：图标按钮 + select 标记叠加。 */
	public class MeshItem extends arc.scene.ui.Button {

		public final TextureRegion icon;

		MeshItem(TextureRegion icon, Runnable clicked) {
			super(Styles.bu);
			this.icon = icon;
			this.clicked = clicked;
			add(new Image(icon)).grow();

			addListener(new ChangeListener() {
				@Override
				public void changed(ChangeEvent event, Element actor) {
					clicked.run();
				}
			});

			// 悬停 → 更新标记 + 通知外部
			addListener(new InputListener() {
				@Override
				public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
					setHovered(true);
				}

				@Override
				public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
					setHovered(false);
				}
			});
		}

		/// 点击回调（选中/取消/切换逻辑在 select() 里）
		final Runnable clicked;

		/** 悬停状态进入/退出。 */
		public void setHovered(boolean on) {
			if (on) {
				if (hoverItem != null && hoverItem != this)
					hoverItem.setHovered(false);
				hoverItem = this;
				if (onHover != null)
					onHover.run();
			} else if (hoverItem == this) {
				hoverItem = null;
				if (onHover != null)
					onHover.run();
			}
		}

	}
}
