package caliniya.vergvoke.ui.windows;

import arc.scene.ui.layout.*;
import arc.util.*;
import caliniya.vergvoke.core.meta.stat.*;

/**
 * 统计信息展示窗口。按 {@link StatType} 分组显示。
 *
 * <p>
 * 先这样了
 */
public class DataWindow extends Window {

	private final StatStack stack;

	public DataWindow(StatStack data) {
		super("@statistics");
		stack = data;
	}

	@Override
	public void main(Table t) {
		t.clear();
		t.left();
		if (stack == null)
			return;

		stack.each(
				d -> {
					t.add(d.data).left().padBottom(2).align(Align.left);
					t.row();
				});
	}
}
