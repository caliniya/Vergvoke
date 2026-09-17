package caliniya.vergvoke.base.type;

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
}
