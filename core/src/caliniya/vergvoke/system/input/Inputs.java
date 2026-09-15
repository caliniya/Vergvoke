package caliniya.vergvoke.system.input;

import caliniya.vergvoke.base.tool.*;

/** 输入控制器集合：统一持有并按顺序更新（不再作为系统注册）。 */
public class Inputs {

    /** 全部输入控制器（按添加顺序更新）。 */
    public static final Ar<caliniya.vergvoke.system.System<?>> inputs = new Ar<>();

    public static void add(caliniya.vergvoke.system.System<?>... newInputs) {
        for (caliniya.vergvoke.system.System<?> input : newInputs) {
            if (input != null && !inputs.contains(input)) {
                inputs.add(input);
            }
        }
    }

    /** 驱动全部输入控制器（顺序 = inputs 数组顺序）。 */
    public static void updateAll() {
        for (caliniya.vergvoke.system.System<?> input : inputs) {
            input.update();
        }
    }
}
