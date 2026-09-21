package caliniya.vergvoke.game.data;

import arc.func.Boolf;
import arc.func.Cons;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;

public class CommandData {
    // 包括UI需要的建筑和单位，资源相关数据。

    // 当前选中的单位
    public static Ar<Unit> checkedUnits = new Ar<Unit>();
    public static boolean commanding;

    /** 指挥状态（直接指挥行单选）。 */
    public enum CommandType {
        None, Move, Stop
    }

    /** 当前指挥状态（默认移动模式）。 */
    public static CommandType commandType = CommandType.Move;

    // 初始化，也包括重置数据
    public static void init() {
        checkedUnits.clear();
    }

    //
    public static void findUnit(float x, float y, Boolf<Unit> F, Cons<Unit> C) {
        Game.team
                .data()
                .find(
                        x,
                        y,
                        100f,
                        e -> {
                            if (e instanceof Unit) {
                                if (F.get((Unit) e)) {
                                    C.get((Unit) e);
                                }
                            }
                        });
    }

    public static void findUnit(float x, float y, Cons<Unit> C) {
        findUnit(
                x,
                y,
                e -> {
                    return true;
                },
                e -> C.get(e));
    }
}
