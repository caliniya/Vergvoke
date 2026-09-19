package caliniya.vergvoke;

import static arc.Core.*;

import arc.*;
import arc.assets.Loadable;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.scene.ui.layout.Scl;
import arc.util.*;
import caliniya.vergvoke.base.shaders.*;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.core.meta.ui.Pal;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.system.input.*;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.ui.*;

public class Vergvoke extends ApplicationCore {

    public boolean assinited = false;
    /** 平台输入：桌面 / 移动二选一（相机 / 宇宙视图 / 选中 / 下令全在 InputProcess 里）。 */
    public InputProcess platformInput;

    // 用于记录开始时间
    private long startTime;

    @Override
    public void setup() {
        // 记录应用启动时的纳秒时间
        startTime = java.lang.System.nanoTime();
        graphics.clear(Color.black);
    }

    @Override
    public void init() {
        Init.init();
        super.init();
    }

    @Override
    public void update() {
        super.update();
        graphics.clear(Color.black);

        // 本帧的帧时间只在这里取一次（Time.delta：60TPS 帧倍率，Arc 自带 3.0 上限），
        // 往下全部靠参数传递：游戏更新 / 渲染 / 调试渲染都用同一个 delta
        float delta = Time.delta;

        // 资源加载完成后的初始化
        if (assets.update() && !assinited) {
            Shaders.load();
            Fonts.setup();
            atlas = assets.get("sprites/sprites.aatls", TextureAtlas.class);
            Styles.load();
            Pal.load();
            UI.initAll();
            UI.Menu();
            UI.Debug();
            // 平台输入：按平台二选一；相机 / 宇宙视图 / 选中 / 下令都在同一个对象里，
            // 不再有"每个输入处理器各自维护运行 / 暂停状态"的麻烦
            platformInput = Core.app.isMobile() ? new MobileInput() : new DesktopInput();
            InputMultiplexer multiplexer = new InputMultiplexer(
                    scene,
                    new GestureDetector(platformInput),
                    platformInput);
            input.addProcessor(multiplexer);
            Contents.load();
            UI.camera.resize(graphics.getWidth(), graphics.getHeight());
            UI.camera.update();
            assinited = true;
            Scl.setProduct(1);

            // 计算消耗时间
            long durationNanos = java.lang.System.nanoTime() - startTime;

            // 转换单位
            long durationMillis = durationNanos / 100_000_0; // 毫秒 (带小数)
            long durationMicros = durationNanos / 1000; // 微秒 (整数)

            Log.info(
                    "Game inited - Using: " + String.format("%d ms / %d µs", durationMillis, durationMicros));
            // DebugRender.it = new DebugRender().init();
        }

        // 加载界面
        if (!assinited) {
            UI.Loading(assets.getProgress());
        } else {
            Draw.proj(camera);

            // 平台输入每帧更新（WASD 平移 / 宇宙相机视口）
            platformInput.update(delta);

            Game.update(delta);

            Render.updateAll(delta);
            camera.update();
        }
        scene.act();
        scene.draw();
        // Draw.flush();

        if (DebugRender.it != null) {
            Draw.proj(UI.camera);
            DebugRender.it.update(delta);
        }

        Draw.flush();
    }

    @Override
    public void add(ApplicationListener module) {
        super.add(module);
        if (module instanceof Loadable l) {
            assets.load(l);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        assets.dispose();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        scene.resize(width, height);
        camera.resize(width, height);
    }

    @Override
    public void pause() {
        Events.fire(new EventType.GamePause(true));
        Log.info("Game Pause");
        super.pause();
    }

    @Override
    public void resume() {
        Events.fire(new EventType.GamePause(false));
        Log.info("Game Resume");
        super.resume();
    }
}
