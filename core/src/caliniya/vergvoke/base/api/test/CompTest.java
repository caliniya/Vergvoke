package caliniya.vergvoke.base.api.test;

import caliniya.vergvoke.annotation.Annotations.*;

@Component(name = "tastComp", proc = "main", index = 1)
public class CompTest {

    @Save(index = 1)
    public int x;

    @Save(index = 2)
    public int y;

    @Updata
    public void test() {
        x++;
        y++;
    }

}
