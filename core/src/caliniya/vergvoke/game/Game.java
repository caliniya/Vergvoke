package caliniya.vergvoke.game;

import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.core.Render;
import caliniya.vergvoke.system.game.GameProcess;
import caliniya.vergvoke.world.stars.StarMap;

public class Game {
    // 表示玩家队伍
    public static TeamTypes team;

    // 表示当前处于的星域
    public static StarMap starMap;

    /** 当前是否在游戏内部（进入游戏后为 true；退出/回菜单时置回 false）。 */
    public static boolean inGame = false;

    /**
     * 游戏内每帧更新（主循环调用）：
     * 过渡期先驱动手写系统，然后走生成侧的统一更新（实体 → 组件系统）。
     */
    public static void update(float delta) {
        if (!inGame) {
            return;
        }

        // 过渡期：手写主线程系统（GameProcess / Render）照旧驱动，等它们迁移进生成侧后删
        if (GameProcess.it != null && GameProcess.it.inited) {
            GameProcess.it.update();
        }
        if (Render.it != null && Render.it.inited) {
            Render.it.update();
        }

        // 生成侧统一更新（帧序：基础更新 → 组件系统 → 实体）
        Systems.updateAll(delta);
    }
}
