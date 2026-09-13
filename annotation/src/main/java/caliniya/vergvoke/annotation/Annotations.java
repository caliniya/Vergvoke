package caliniya.vergvoke.annotation;

import java.lang.annotation.*;

/**
 * Vergvoke注解集合。
 *
 * <p>
 * 这些注解都是 {@link RetentionPolicy#SOURCE} 级别的——只用于编译期，由注解处理器在编译时读取并生成代码，
 * 运行期不会有任何开销。
 *
 */
public class Annotations {

    /**
     * 声明一个"组件"。
     *
     * <p>
     * 组件是纯数据类：它的每个非 {@code static}、非 {@link Import} 字段都会被注入到
     * 引用它的实体的生成类里（字段名在同一个实体中必须唯一）。被处理的逻辑由 {@code proc}
     * 指定的系统负责。
     *
     * <p>
     * 注意：组件类本身不会被实例化——生成的实体类里只有字段。
     *
     * <pre>{@code
     * @Component(name = "vel", proc = "MoveSystem")
     * public class VelocityComp {
     *     public float vx;
     *     public float vy;
     *
     *     @Updata
     *     public void update() { // 标记给系统调用
     *         vx *= 0.99f;
     *     }
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface Component {

        /**
         * 组件名字。用于日志、错误信息，以及生成代码里的标识。
         *
         * <p>
         * 例：{@code name = "health"}
         */
        String name();

        /**
         * 由哪一个系统处理自己（系统名字）。
         *
         * <p>
         * 默认空字符串表示不由任何系统处理。
         */
        String proc() default "";
    }

    /**
     * 说明"这个字段是从实体中其他组件读来的"。
     *
     * <p>
     * 被标记的字段不会注入到实体的生成类里，从而避免与其他组件的同名字段互相覆盖；
     * 它会去别处的组件里找同名同类型的字段来用。
     *
     * <p>
     * 如果没有找到这个字段（或者类型不一致），编译期会直接报错：
     * 
     * <pre>{@code @Import field 'xxx' has no matching field in another component}</pre>
     *
     * <p>
     * 只能用在 {@link Component} 的字段上，也不能用在 {@code static} 字段上。
     *
     * <pre>{@code
     * @Component(name = "render", proc = "RenderSystem")
     * public class RenderComp {
     *     @Import
     *     public float x; // 直接借用位置组件里的 x，不再单独存一份
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.FIELD })
    public @interface Import {
    }

    /**
     * 声明一种"实体"。
     *
     * <p>
     * 实体 = 一组组件的集合。编译期会为它生成一个类（名字是 {@code name + "Entity"}），
     * 把所有组件的字段"拍平"进去；同一实体里字段不允许重名，组件也不允许重复。
     *
     * <pre>{@code
     * @Entity(name = "unit", comps = { HealthComp.class, VelocityComp.class })
     * public class UnitDef {
     * }
     * // 会生成 caliniya.vergvoke.base.ecs.unitEntity，里面同时有 health / vx / vy 等字段
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface Entity {

        /**
         * 这个实体包含哪些组件（写组件的 {@code .class}）。
         *
         * <p>
         * 每个类都必须是 {@link Component}；同一个组件只能出现一次。
         */
        Class<?>[] comps();

        /**
         * 实体名字。生成的类名是 {@code name + "Entity"}，所以要用合法标识符。
         *
         * <p>
         * 例：{@code name = "unit"} → 生成 {@code unitEntity}
         */
        String name();
    }

    /**
     * 声明一个"系统"。
     *
     * <p>
     * 系统负责处理组件（上面 {@link Component#proc()} 里写的就是系统名），
     * 可以按 {@link #index()} 排序，也可以扔到某个 {@link ThreadDef} 指定的线程里跑。
     *
     * <pre>{@code
     * @SystemDef(name = "MoveSystem", thread = "logic", index = 10)
     * public class MoveSystem {
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface SystemDef {

        /**
         * 系统名字。要和 {@link Component#proc()} 里写的名字对得上。
         */
        String name();

        /**
         * 这个系统跑在哪个线程上（对应 {@link ThreadDef} 的名字）。
         */
        String thread();

        /**
         * 同一线程内的执行顺序：数值越小越先跑。
         */
        int index();
    }

    /**
     * 声明一个"线程"。
     *
     * <p>
     * {@link SystemDef#thread()} 里引用的名字就是这里定义的名字；
     * 编译期据此把系统分配到对应线程。
     *
     * <pre>{@code
     * @ThreadDef(name = "logic")
     * public class LogicThread {
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface ThreadDef {

        /**
         * 线程名字（在 {@link SystemDef#thread()} 中被引用）。
         */
        String name();
    }

    /**
     * 标记组件的"每帧更新方法"。
     *
     * <p>
     * 被打标的方法表示"这个方法需要被系统在更新时调用"，注解处理器会把它收集起来，
     * 由对应的系统来驱动。只能用在方法上。
     *
     * <pre>{@code
     * @Component(name = "fuel", proc = "FuelSystem")
     * public class FuelComp {
     *     public float amount;
     *
     *     @Updata
     *     public void burn() {
     *         amount -= 1f;
     *     }
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.METHOD })
    public @interface Updata {
    }
}
