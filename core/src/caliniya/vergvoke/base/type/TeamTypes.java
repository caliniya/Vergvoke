package caliniya.vergvoke.base.type;

import arc.Core;
import caliniya.vergvoke.game.data.TeamData;
import caliniya.vergvoke.game.*;

public enum TeamTypes {
    Evoke,
    Veto,
    Abort,
    Mutex;

    /** 快捷获取该团队的运行时数据 */
    public TeamData data() {
        return Teams.get(this);
    }

    /**
     * 本地化阵营名（键 {@code team.Xxx}，如 "Evoke"）。
     *
     * <p>做成方法而不是 {@code final} 字段：本枚举在游戏很早期就会被触碰，
     * 而 {@code Core.bundle} 要等 {@code Init} 走到一半才建好，写成字段会有初始化顺序坑
     * （{@code DamageType} 那种写法能成立只是因为它第一次被摸到时 bundle 已经在了）。
     */
    public String localizedName() {
        return Core.bundle == null ? name() : Core.bundle.get("team." + name(), name());
    }
}
