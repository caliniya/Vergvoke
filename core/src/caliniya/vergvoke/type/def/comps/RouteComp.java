package caliniya.vergvoke.type.def.comps;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.Ar;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.game.data.RouteData;
import caliniya.vergvoke.game.data.WorldData;

/**
 * 导航组件：路径 + 每帧期望速度向量（"想走"的那一半）。
 *
 * <p>
 * 分工：proc 挂在 {@code RouteSystem}（route 线程）上，本组件的 @Updata 由导航线程驱动——
 * 算路径、沿路径给出速度向量；把速度积到坐标上（"走"）仍归 {@link MoveComp}（主线程）。
 *
 * <p>
 * 到达 / 不可达时只清速度、置空路径（组件源码里拿不到实体引用，摘出 moveunits 由
 * RouteSystem 调用后检查 {@code path == null} 完成）。
 */
@Component(name = "Route", index = 7, proc = "RouteSystem")
public class RouteComp {

    /** 是否已经按当前目标寻过路（指挥改目标后要置回 false） */
    public boolean pathed;

    /** 移动目标点 */
    @Save(index = 1)
    public float targetX;

    @Save(index = 2)
    public float targetY;

    /** 当前路径节点（导航线程写；null = 已到达 / 不可达 / 尚未寻路） */
    public Ar<Point2> path;

    /** 正在走的路径节点下标 */
    public int pathIndex = 0;

    @Import
    public float x;

    @Import
    public float y;

    /** 以下借 MoveComp 的运动字段（@Updata 体里读写它们，实体里只有一份） */
    @Import
    public float speed;

    @Import
    public float speedX;

    @Import
    public float speedY;

    @Import
    public float angle;

    @Updata
    public void update(float delta) {
        // 路径请求
        if (!pathed) {
            boolean pathFound = calculatePath();
            pathed = true;

            if (!pathFound) return;
        }

        // 向量计算（每一帧都根据当前位置计算期望速度向量）
        calculateVelocityVector();
    }

    /**
     * 计算路径
     * @return true 如果找到路径，false 如果不可达
     */
    private boolean calculatePath() {
        int sx = (int) (x / WorldData.TILE_SIZE);
        int sy = (int) (y / WorldData.TILE_SIZE);
        int tx = (int) (targetX / WorldData.TILE_SIZE);
        int ty = (int) (targetY / WorldData.TILE_SIZE);

        // 如果已经在同一个格子，或者需要寻路
        if (sx != tx || sy != ty) {
            // 这里的 2, 1 暂时硬编码（unitSize / capability）
            path = RouteData.findPath(sx, sy, tx, ty, 2, 1);

            if (path == null) {
                stopMoving();
                return false;
            }

            if (!path.isEmpty()) {
                path.remove(0); // 移除起点
            }
            pathIndex = 0;
        } else {
            // 起点终点重合，直接走直线，清空 path 让 vector 计算接管
            if (path != null) path.clear();
        }
        return true;
    }

    private void calculateVelocityVector() {
        if (path == null) return;

        // 判定到达节点的阈值，稍微宽容一点避免在节点附近抖动
        float nodeReachTolerance = 4f;

        float nextX, nextY;
        boolean isFinalTarget = false;

        if (path.isEmpty()) {
            // 情况 A: 同格移动，直接去最终目标
            nextX = targetX;
            nextY = targetY;
            isFinalTarget = true;
        } else {
            // 情况 B: 沿路径移动
            if (pathIndex >= path.size) {
                // 容错：越界视为到达最后
                nextX = targetX;
                nextY = targetY;
                isFinalTarget = true;
            } else if (pathIndex == path.size - 1) {
                // 最后一个节点：去往精确坐标
                nextX = targetX;
                nextY = targetY;
                isFinalTarget = true;
            } else {
                // 中间节点：去往网格中心
                Point2 node = path.get(pathIndex);
                nextX = node.x * WorldData.TILE_SIZE + WorldData.TILE_SIZE / 2f;
                nextY = node.y * WorldData.TILE_SIZE + WorldData.TILE_SIZE / 2f;
                isFinalTarget = false;
            }
        }

        float dist = Mathf.dst(x, y, nextX, nextY);

        if (!isFinalTarget && dist <= speed + nodeReachTolerance) {
            pathIndex++;
            calculateVelocityVector();
            return;
        }
        if (isFinalTarget && dist <= speed) {
            speedX = 0;
            speedY = 0;
            stopMoving();
        } else {
            angle = Angles.angle(x, y, nextX, nextY);
            speedX = Mathf.cosDeg(angle) * speed;
            speedY = Mathf.sinDeg(angle) * speed;
        }
    }

    /** 到达 / 不可达：清速度、置空路径（RouteSystem 看到 path == null 会把实体摘出 moveunits） */
    private void stopMoving() {
        speedX = 0;
        speedY = 0;
        path = null;
    }
}
