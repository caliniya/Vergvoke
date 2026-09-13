package caliniya.vergvoke.annotation;

import java.lang.annotation.*;

public class Annotations {

    // 声明一个组件
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface Component {
        String name();// 组件名字

        String proc() default "";// 由哪一个系统处理自己，为空字符串表示不由任何系统处理
    }

    // 这说明这个字段是从实体中其他组件读来的，不应该将它注入到实体中以避免覆盖
    // 如果没有找到这个字段 那么就应该报错
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.FIELD })
    public @interface Import {
    }

    // 声明一种实体
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface Entity {
        Class<?>[] comps();// 实体包含哪些组件

        String name();
    }

    // 声明一个系统
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface SystemDef {
        String name();

        String thread();

        int index();
    }

    // 声明一个线程
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.TYPE })
    public @interface ThreadDef {
        String name();
    }

    // 标记组件的更新方法
    @Retention(RetentionPolicy.SOURCE)
    @Target({ ElementType.METHOD })
    public @interface Updata {
    }
}