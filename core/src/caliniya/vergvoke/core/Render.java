package caliniya.vergvoke.core;

import arc.Core;
import arc.graphics.Camera;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.game.Game;
import caliniya.vergvoke.game.data.WorldData;

/**
 * 全局渲染处理
 * 负责管理游戏相机的缩放、震动效果和边界限制。
 * 宇宙视图下切换至无边界限制的 universeCamera。
 */
public class Render extends caliniya.vergvoke.system.System<Render> {

    /** 全局实例（由 Data.loadSystems() 创建，负责相机缩放 / 震动等每帧逻辑）。 */
    public static Render it;

    // --- 静态变量 ---

    /** 当前相机缩放级别 */
    public static float currentZoom = 1.0f;

    /** 最小允许的缩放级别 */
    public static float minZoom = 0.25f;

    /** 最大允许的缩放级别 */
    public static float maxZoom = 4.0f;

    // --- 震屏参数 ---

    /** 世界相机的震动强度 */
    private static float shakeIntensity = 0f;

    /** UI相机的震动强度 */
    private static float uiShakeIntensity = 0f;

    /** 世界相机的震动偏移向量 */
    private static final Vec2 shakeOffset = new Vec2();

    /** UI相机的震动偏移向量 */
    private static final Vec2 uiShakeOffset = new Vec2();

    /** 宇宙视图相机 —— 无边界限制，自由移动 */
    public static Camera universeCamera;

    /** 渲染管线：并入的子渲染器（按数组顺序逐个更新）。 */
    public static final Ar<caliniya.vergvoke.system.System<?>> renders = new Ar<>();

    /**
     * 初始化
     */
    @Override
    public Render init() {
        index = 9;

        // 重置状态
        currentZoom = 1.0f;
        shakeIntensity = 0f;
        uiShakeIntensity = 0f;
        shakeOffset.setZero();
        uiShakeOffset.setZero();

        // 初始化宇宙相机
        universeCamera = new Camera();
        universeCamera.width = Core.graphics.getWidth();
        universeCamera.height = Core.graphics.getHeight();
        universeCamera.position.set(Core.camera.position);

        return super.init();
    }

    /**
     * 触发全局震屏效果
     */
    public static void shake(float intensity) {
        shakeIntensity = Math.max(shakeIntensity, intensity);
    }

    /**
     * 触发带有距离衰减的局部震屏效果
     */
    public static void shake(float intensity, float range, float x, float y) {
        float dist = Core.camera.position.dst(x, y);
        if (dist < range) {
            float realIntensity = intensity * (1f - dist / range);
            shake(realIntensity);
        }
    }

    /**
     * 同时触发世界相机和UI相机的强烈震屏效果
     */
    public static void shakeBig(float intensity) {
        shakeIntensity = Math.max(shakeIntensity, intensity);
        uiShakeIntensity = Math.max(uiShakeIntensity, intensity);
    }

    /**
     * 直接设置当前的缩放级别
     * 会自动限制在minZoom和maxZoom允许的范围内
     */
    public static void setZoom(float value) {
        currentZoom = Mathf.clamp(value, minZoom, maxZoom);
    }

    /**
     * 基于当前缩放级别进行增量缩放
     */
    public static void zoom(float amount) {
        setZoom(currentZoom + amount);
    }

    /**
     * 每帧更新逻辑
     * 宇宙视图下跳过游戏相机处理，仅更新 universeCamera。
     */
    @Override
    public void update() {

        // 1. 撤销上一帧的震动偏移，恢复相机逻辑位置
        Core.camera.position.sub(shakeOffset);

        // 2. 应用缩放参数到相机视口
        float targetWidth = Core.graphics.getWidth() * currentZoom;
        float targetHeight = Core.graphics.getHeight() * currentZoom;
        Core.camera.width = targetWidth;
        Core.camera.height = targetHeight;

        // 3. 计算新的世界震动偏移
        if (shakeIntensity > 0.1f) {
            shakeOffset.setToRandomDirection().scl(Mathf.random(shakeIntensity));
            shakeIntensity = Mathf.lerpDelta(shakeIntensity, 0, 0.1f);
        } else {
            shakeOffset.setZero();
            shakeIntensity = 0f;
        }

        // 4. 应用新的震动偏移
        Core.camera.position.add(shakeOffset);

        // 5. 限制相机边界
        clampCamera();

        // 6. 处理 UI 相机震动
        float uiCenterX = Core.graphics.getWidth() / 2f;
        float uiCenterY = Core.graphics.getHeight() / 2f;

        if (uiShakeIntensity > 0.1f) {
            uiShakeOffset.setToRandomDirection().scl(Mathf.random(uiShakeIntensity));
            UI.camera.position.set(uiCenterX, uiCenterY).add(uiShakeOffset);
            uiShakeIntensity = Mathf.lerpDelta(uiShakeIntensity, 0, 0.1f);
        } else {
            uiShakeIntensity = 0f;
            uiShakeOffset.setZero();
            UI.camera.position.set(uiCenterX, uiCenterY);
        }
    }

    /**
     * 限制相机中心点不超过世界地图的物理边界
     */
    private static void clampCamera() {
        if (WorldData.world == null)
            return;

        float mapW = WorldData.world.W * WorldData.TILE_SIZE;
        float mapH = WorldData.world.H * WorldData.TILE_SIZE;

        Core.camera.position.x = Mathf.clamp(Core.camera.position.x, 0, mapW);
        Core.camera.position.y = Mathf.clamp(Core.camera.position.y, 0, mapH);

        universeCamera.position.x = Mathf.clamp(universeCamera.position.x, 0, Game.starMap.w);
        universeCamera.position.y = Mathf.clamp(universeCamera.position.y, 0, Game.starMap.h);
    }

    /** 驱动全部子渲染器（顺序 = renders 数组顺序），最后统一 flush。 */
    public static void updateAll() {
        for (caliniya.vergvoke.system.System<?> render : renders) {
            render.update();
        }
        Draw.flush();
    }

    /**
     * 销毁系统资源
     */
    @Override
    public void dispose() {
        super.dispose();
        currentZoom = 1.0f;
        shakeIntensity = 0f;
        uiShakeIntensity = 0f;
        shakeOffset.setZero();
        uiShakeOffset.setZero();
    }
}
