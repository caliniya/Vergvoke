package caliniya.vergvoke.base.api.test;

import caliniya.vergvoke.annotation.Annotations.*;

/** 系统夹具：@SystemDef 类继承系统基类，行为通过 update 注入。 */
@SystemDef(name = "testSystem", thread = "test", index = 1)
public class SystemTest extends caliniya.vergvoke.system.System<SystemTest> {

    @Override
    public void update(float delta) {
    }

}
