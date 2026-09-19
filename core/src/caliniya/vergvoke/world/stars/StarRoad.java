package caliniya.vergvoke.world.stars;

import arc.func.*;
import arc.graphics.g2d.Lines;
import arc.math.geom.*;
import arc.math.geom.QuadTree.*;

public class StarRoad implements QuadTreeObject {

    public StarNode A, B;
    public float x1, y1, dx, dy;

    // 通常不会主动使用它
    protected StarRoad(StarNode A, StarNode B) {
        this.A = A;
        this.B = B;
        x1 = Math.min(A.x, B.x);
        y1 = Math.min(A.y, B.y);
        dx = Math.abs(A.x - B.x);
        dy = Math.abs(A.y - B.y);
    }

    // 我是个天才)
    public static void with(StarNode A, StarNode B, Cons<StarRoad> use) {
        use.get(new StarRoad(A, B));
    }

    public void draw() {
        Lines.line(A.x, A.y, B.x, B.y);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StarRoad) {
            StarRoad s = (StarRoad) o;
            return (this.A == s.A && this.B == s.B) || (this.B == s.A && this.A == s.B);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return (this.A == null ? 0 : this.A.hashCode()) + (this.B == null ? 0 : this.B.hashCode());
    }

    @Override
    public void hitbox(Rect box) {
        box.set(x1, y1, dx, dy);
    }
}
