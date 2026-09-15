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
         * 写 {@code "main"} 或留空 = "轻量档"：它的 {@code @Updata} 会被直接铺进实体 {@code update()}
         * 里，按 {@link #index()} 顺序执行；写其他系统名 = "独立档"：生成 {@code update_<组件简单名>()}，
         * 由那个系统在自己的阶段里调用。
         */
        String proc() default "";

        /**
         * 组件序号（数值小的先处理）。<b>任何组件都必须填一个非 0 的值</b>。
         *
         * <p>
         * 有两个用途：
         *
         * <ul>
         *   <li><b>序列化顺序</b>：实体 {@code write()} / {@code read()} 时按这个顺序逐个组件写出、读入
         *       （组件内部再按 {@link Save#index()} 排）；
         *   <li><b>轻量档更新顺序</b>：{@code proc = "main"} / 空时，{@code @Updata} 方法体在实体
         *       {@code update()} 里的先后顺序（独立档的顺序由系统自己的 index 决定）。
         * </ul>
         *
         * <p>
         * 同一个实体里的两个组件不允许用同一个 index（先后顺序会变得不确定，编译期直接报错）。
         */
        int index() default 0;

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
        /**
         * 这个系统跑在哪个线程上。
         *
         * <p>
         * 写 {@code "main"} 或 {@code "test"} 表示直接跑在主线程上（保留名，不需要 {@link ThreadDef}
         * 声明）；其他值必须有一个同名的 {@link ThreadDef}。
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
     * 标记组件的"更新方法"（每帧执行）。
     *
     * <p>
     * 要求：非 static、无参数、返回 void，而且<b>一个组件最多只能有一个</b> {@code @Updata} 方法。
     *
     * <p>
     * 方法体会在编译期被复制进实体的生成类，走哪一档取决于所在组件的 {@link Component#proc()}：
     *
     * <ul>
     *   <li>轻量档（{@code proc = "main"} 或留空）→ 方法体<b>直接铺进实体的 {@code update()}</b>，
     *       顺序由 {@link Component#index()} 决定（该档必须提供非 0 的 index）；
     *   <li>独立档（{@code proc = 其他系统名}）→ 生成独立方法 {@code update_<组件简单名>()}，
     *       由该系统在自己的阶段里调用。
     * </ul>
     *
     * <pre>{@code
     * @Component(name = "fuel", proc = "main", index = 20)
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

    /**
     * 标记组件里"需要序列化的字段"。
     *
     * <p>
     * 组件的序列化顺序 = 所在组件的 {@link Component#index()}（组件之间）→ 本注解的 {@link #index()}
     * （组件内部）。实体的生成类会按这个顺序把字段直接写进 {@code Writes}、再按同样的顺序读回来。
     *
     * <p>
     * 支持直接序列化的类型：{@code boolean / byte / short / char / int / long / float / double / String}。
     * 其他类型（数组、集合、别的对象……）编译期会报错，请改用 {@link Write} / {@link Read} 手写。
     * {@code char} 按 16 位整数存；{@code String} 为 {@code null} 时按空串写出（读回来是空串）。
     *
     * <p>
     * 不能用在 {@code static} 字段上，也不能和 {@link Import} 同时用（{@code @Import} 是借来的字段，
     * 由它真正的宿主组件负责序列化），并且一个组件里不能同时用 {@code @Save} 和 {@code @Write} /
     * {@code @Read}（二选一）。
     *
     * <pre>{@code
     * @Component(name = "health", index = 10)
     * public class HealthComp {
     *     @Save(index = 1)
     *     public float health;
     *
     *     @Save(index = 2)
     *     public float maxHealth;
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.FIELD })
    public @interface Save {

        /**
         * 组件内部的字段序列化顺序：数值小的先写、先读（同一个组件里不允许重复）。
         */
        int index();
    }

    /**
     * 标记组件的"序列化方法"。
     *
     * <p>
     * 给那些没法用 {@link Save} 简单写出的组件用（数组、集合、需要自己写数量前缀的数据……）：
     * 方法体在编译期被<b>原样铺进实体的 {@code write()}</b>（不生成中间方法），所以方法体里可以直接用组件自己的字段；
     * 参数名保持你写的样子（和实体的 {@code w} 不同名时，包装里会自动起个别名）。
     *
     * <p>
     * 生成代码会给方法体套一层包装（一层 {@code { }}），方法体内部的代码块照旧。
     * 正因为是内联的，方法体里的 <b>{@code return} 等于"出问题了"</b>：包装里会带一层守卫，
     * 中途 return 出去就 {@code throw new IllegalStateException(...)}，
     * 而不是悄悄跳过后面所有组件。（所以正常逻辑请用 {@code if / else} 收尾，别用 return。）
     *
     * <p>
     * 签名要求：非 static、返回 void、只有一个参数，参数类型必须是 {@code arc.util.io.Writes}。
     * 一个组件最多一个 {@code @Write}，而且必须和 {@link Read} 成对出现。
     *
     * <pre>{@code
     * @Component(name = "armor", index = 20)
     * public class ArmorComp {
     *     public float[] resist;
     *
     *     @Write
     *     public void save(Writes w) {
     *         w.i(resist.length);
     *         for (float v : resist) w.f(v);
     *     }
     *
     *     @Read
     *     public void load(Reads r) {
     *         resist = new float[r.i()];
     *         for (int i = 0; i < resist.length; i++) resist[i] = r.f();
     *     }
     * }
     * }</pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.METHOD })
    public @interface Write {
    }

    /**
     * 标记组件的"反序列化方法"，和 {@link Write} 成对使用。
     *
     * <p>
     * 签名要求：非 static、返回 void、只有一个参数，参数类型必须是 {@code arc.util.io.Reads}。
     *
     * @see Write
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.METHOD })
    public @interface Read {
    }
}
