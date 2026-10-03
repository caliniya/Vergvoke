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

    private String cachedName;

    /**
     * 本地化阵营名（键 {@code team.Xxx}，如 "Evoke"）。
     *
     * <p>做成方法而不是 {@code final} 字段：本枚举在游戏很早期就会被触碰，
     * 而 {@code Core.bundle} 要等 {@code Init} 走到一半才建好，写成字段会有初始化顺序坑
     * （{@code DamageType} 那种写法能成立只是因为它第一次被摸到时 bundle 已经在了）。
     *
     * <p>带缓存：drawTeamTag 每单位每帧调用，{@code "team." + name()} 的字符串拼接
     * 不能放热路径。bundle 未就绪时返回原名且<b>不缓存</b>（避免把回退名钉死），
     * 就绪后的第一次调用才缓存——语言在启动时固定，缓存不会失效。
     */
    public String localizedName() {
        if (cachedName != null) return cachedName;
        if (Core.bundle == null) return name();
        cachedName = Core.bundle.get("team." + name(), name());
        return cachedName;
    }
}
