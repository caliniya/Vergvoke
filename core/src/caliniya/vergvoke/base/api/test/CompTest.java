package caliniya.vergvoke.base.api.test;

import caliniya.vergvoke.annotation.Annotations.*;

@Component(name = "tastComp", proc = "main")
public class CompTest {
    
    public int x;
    public int y;

    @Updata
    public void test() {
        x++;
        y++;
    }

}
