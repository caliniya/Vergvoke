package caliniya.vergvoke.ui.windows;

import arc.scene.ui.layout.Table;
import arc.util.Align;
import caliniya.vergvoke.core.meta.stat.*;

/**
 * 统计信息展示窗口。按 {@link StatType} 分组显示。
 *
 * <p>
 * 先这样了
 */
public class DataWindow extends Window {

    /** 每层缩进的字符（1 个全角空格 = 1 个汉字宽度）。若 1 层不够醒目，改成 {@code "\u3000\u3000"}。 */
    static final String INDENT = "\u3000\u3000";

    private StatStack stack;

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

        // 完整遍历：所有 StatData 的 data 已含缩进，直接显示；跳过空内容（无分组空标题）
        stack.each(
                d -> {
                    t.add(d.data).left().padBottom(2).align(Align.left);
                    t.row();
                });
    }
}
