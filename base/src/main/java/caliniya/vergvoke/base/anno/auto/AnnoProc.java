package caliniya.vergvoke.base.anno.auto;

import java.lang.annotation.*;

/**
 * 这个注解表示这是一个注解处理器
 * 
 * <p>
 * 被标记的注解处理器会自动被 {@link caliniya.vergvoke.annotation.Processor} 识别并调用。
 *
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AnnoProc {
}
