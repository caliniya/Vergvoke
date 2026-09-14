package caliniya.vergvoke.base.api.test;

import arc.util.io.*;
import caliniya.vergvoke.annotation.Annotations.*;

/** 演示"没法简单序列化"的组件：用 @Write / @Read 手写。 */
@Component(name = "test2", proc = "", index = 2)
public class CompTest2 {

    public int xxxxx;

    public int yyyyy;

    @Write
    public void save(Writes out) {
        out.i(xxxxx);
        out.i(yyyyy);
    }

    @Read
    public void load(Reads in) {
        xxxxx = in.i();
        yyyyy = in.i();
    }
}
