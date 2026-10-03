package caliniya.vergvoke.ui.windows;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import caliniya.vergvoke.core.meta.stat.StatStack;
import caliniya.vergvoke.core.meta.stat.StatType;
import caliniya.vergvoke.world.stars.StarNode;

/**
 * 星域节点信息窗：宇宙视图点击节点时在屏幕右侧打开的竖窗。
 *
 * <p>内容复用 {@link StatStack} 统计渲染（DataWindow 同款按分组展示）。
 * 内容是占位性质的骨架——坐标 / 对应地图 / 连接列表，往后按星域玩法逐步充实
 * （所属势力、探索状态、进入星域按钮等）。
 */
public class StarNodeWindow extends Window {

    private final StarNode node;

    public StarNodeWindow(StarNode node) {
        super(node.localizedName);
        this.node = node;
        // 窄栏宽度（Y 轴在 build 里占满全屏）
        w = 320f;
        // 定制尺寸（Y 全屏 + 右侧栏）由 build 覆写，全屏切换按钮会破坏这个布局
        showFullButton = false;
    }

    @Override
    public void main(Table t) {
        StatStack stack = new StatStack();

        stack.add("坐标：" + (int) node.x + ", " + (int) node.y, StatType.none);
        stack.add("对应地图：" + node.mapName(), StatType.general);
        stack.add("连接星域：" + node.nei.size, StatType.general);
        if (node.nei.size > 0) {
            stack.add("—— 连接列表 ——", StatType.function);
            for (StarNode n : node.nei) {
                stack.add("→ " + n.localizedName, StatType.function);
            }
        }

        stack.each(
                d -> {
                    t.add(d.data).left().padBottom(2).align(Align.left);
                    t.row();
                });
    }

    @Override
    public void build() {
        super.build();
        // 侧栏式布局：Y 轴占满全屏、X 轴窄栏，贴右侧边缘（基类 build 默认居中 + 最小宽 3/7 屏，这里覆写）
        float targetW = Math.min(w, Core.scene.getWidth() * 0.4f);
        window.setSize(targetW, Core.scene.getHeight());
        window.setPosition(Core.scene.getWidth() - targetW, 0f);
    }
}
