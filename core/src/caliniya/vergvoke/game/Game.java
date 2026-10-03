package caliniya.vergvoke.game;

import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.world.stars.StarMap;

public class Game {
    // 表示玩家队伍
    public static TeamTypes team;

    // 表示当前处于的星域
    public static StarMap starMap;

    /** 当前是否在游戏内部（进入游戏后为 true；退出/回菜单时置回 false）。 */
    public static boolean inGame = false;

    /**
     * 焦点丢失暂停（切出去/截图时为 true）：主线程模拟冻结，画面保持，
     * 背景线程由 System 基类监听同一事件自行暂停。由 Vergvoke.pause/resume 维护。
     */
    public static boolean focusPaused = false;

    /**
     * 游戏内每帧更新（主循环调用）：
     * 统一走生成侧（帧序：基础更新 → 组件系统 → 实体）。
     * 手写主线程系统（GameProcess / Render）已挂进 {@code Systems.systems}，由组件系统阶段一起驱动。
     */
    public static void update(float delta) {
        if (!inGame || focusPaused) {
            return; // 焦点暂停时画面冻结但继续渲染；背景线程由 GamePause 事件各自挂起
        }

        // 生成侧统一更新（帧序：基础更新 → 组件系统 → 实体）
        Systems.updateAll(delta);
    }
}
