package caliniya.vergvoke.annotation;

import java.lang.annotation.*;

/**
 * Vergvoke ECS 注解集合。全部为 {@link RetentionPolicy#SOURCE}，编译期生成代码，运行期零开销。
 */
public class Annotations {

    /**
     * 声明组件（纯数据类）。非 static、非 {@link Import} 的字段会注入到引用它的实体生成类里。
     * 
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface Component {

        /** 组件名，用于日志与错误信息。 */
        String name();

        /**
         * 处理该组件的系统名。
         * 留空或 {@code "main"}：{@code @Updata} 直接铺进实体 {@code update()}；
         * 其他系统名：生成 {@code update_<组件简单名>()}，由该系统调用。
         */
        String proc() default "";

        /**
         * 组件序号，必须非 0。决定序列化顺序，以及{@code @Updata} 的注入顺序
         * 同一实体内不允许重复。
         */
        int index();
    }

    /**
     * 字段从实体内其他组件借用，不注入实体（避免同名字段冲突）。
     * 必须能在别的组件里找到同名同类型字段，否则编译报错。
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.FIELD })
    public @interface Import {
    }

    /**
     * 声明实体 = 一组组件的集合。编译期在 {@code caliniya.vergvoke.base.ecs} 包下生成<b>同名</b>类
     * （名字就是 {@link #name()}，不加后缀），把各组件字段拍平进去。
     * 字段名、组件均不允许重复。
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface Entity {

        /** 包含的组件（{@code .class} 列表，每个必须是 {@link Component}）。 */
        Class<?>[] comps();

        /** 实体名：生成的类名就是它本身（不加后缀），放在 {@code caliniya.vergvoke.base.ecs} 包。 */
        String name();
    }

    /**
     * 声明系统。组件的 {@link Component#proc()} 用这里的 {@link #name()} 关联。
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface SystemDef {

        /** 系统名，须与 {@link Component#proc()} 对应。 */
        String name();

        /**
         * 所在线程。{@code "main"} / {@code "test"} 为主进程保留名；
         * 其他值必须有同名 {@link ThreadDef}。
         */
        String thread();

        /** 同线程内执行顺序，小的先跑。 */
        int index();
    }

    /** 声明线程，供 {@link SystemDef#thread()} 引用。 */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface ThreadDef {

        /** 线程名。 */
        String name();
    }

    /**
     * 组件的每帧更新方法。要求：非 static、无参、void；一个组件最多一个。
     * 方法体按 {@link Component#proc()} 决定铺进实体 {@code update()} 还是生成独立方法。
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.METHOD })
    public @interface Updata {
    }

    /**
     * 标记需要序列化的字段。支持基本类型与 {@code String}；其他类型用 {@link Write}/{@link Read}。
     * 不能是 static，不能与 {@link Import} 或 {@link Write}/{@link Read} 混用。
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.FIELD })
    public @interface Save {

        /** 组件内部字段顺序，小的先写读；同组件内不可重复。 */
        int index();
    }

    /**
     * 手写序列化（数组/集合等复杂类型）。方法体在编译期复制进实体。
     * 签名：非 static、void、单参数 {@code Writes}；须与 {@link Read} 成对。
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.METHOD })
    public @interface Write {
    }

    /**
     * 手写反序列化。签名：非 static、void、单参数 {@code Reads}；须与 {@link Write} 成对。
     *
     * @see Write
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.METHOD })
    public @interface Read {
    }
}
