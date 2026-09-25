package caliniya.vergvoke.ui.fragment;

import arc.Core;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.util.OS;
import caliniya.vergvoke.base.ecs.EntityArs;
import caliniya.vergvoke.game.data.CommandData;
import caliniya.vergvoke.game.data.WorldData;

public class DebugFragment {

    private Table root;

    public void add() {
        if (root != null && root.parent != null)
            return;

        root = new Table();
        root.setFillParent(true);
        root.touchable = Touchable.disabled;
        root.top().right();

        root.table(
                t -> {
                    t.label(
                            () -> {
                                // 基础系统信息 (始终可显示)
                                StringBuilder sb = new StringBuilder();
                                sb.append("FPS: ").append(Core.graphics.getFramesPerSecond()).append("\n");
                                sb.append("Mem: ")
                                        .append(Core.app.getJavaHeap() / 1024 / 1024)
                                        .append("  +  ")
                                        .append(Runtime.getRuntime().totalMemory() / 1024 / 1024)
                                        .append("  +  ")
                                        .append(Runtime.getRuntime().freeMemory() / 1024 / 1024)
                                        .append("  all  ")
                                        .append(" MB\n");

                                // 世界数据检查
                                if (WorldData.world == null) {
                                    sb.append("World Data: null\n");
                                } else {

                                    int unitCount = EntityArs.Unit.size();
                                    int buildingCount = (WorldData.buildings != null) ? WorldData.buildings.size() : 0;

                                    sb.append("Units: ").append(unitCount).append("\n");
                                    sb.append("Selected: ").append(CommandData.checkedUnits.size).append("\n");
                                    sb.append("Buildings: ").append(buildingCount).append("\n");
                                    sb.append("Map: ")
                                            .append(WorldData.world.W)
                                            .append("x")
                                            .append(WorldData.world.H)
                                            .append("\n");
                                    // sb.append(WorldData.buildings.isEmpty());
                                }

                                // sb.append("timedelta").append(Time.delta);

                                sb.append("Java: ").append(OS.javaVersion);
                                sb.append("Android: ").append(Core.app.getVersion()).append("\n");

                                return sb.toString();
                            })
                            .color(Color.white);
                })
                .margin(10f);

        Core.scene.add(root);
    }

    public void remove() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
