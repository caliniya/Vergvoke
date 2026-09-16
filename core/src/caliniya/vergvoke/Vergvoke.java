package caliniya.vergvoke;

import static arc.Core.*;

import arc.*;
import arc.assets.Loadable;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.scene.Scene;
import arc.scene.ui.layout.Scl;
import arc.util.Log;
import arc.util.viewport.ScreenViewport;
import caliniya.vergvoke.base.shaders.*;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.core.UI;
import caliniya.vergvoke.core.Render;
import caliniya.vergvoke.core.meta.ui.Pal;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.system.*;
import caliniya.vergvoke.system.input.*;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.system.world.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.ui.fragment.*;

public class Vergvoke extends ApplicationCore {

  public boolean assinited = false;
  public CameraInput camInput;
  public UniverseCameraInput uniInput;
  public UniverseInput unInput;

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
      UnitControl unitCtrl = new UnitControl().init();
      camInput = new CameraInput().init();
      uniInput = new UniverseCameraInput().init();
      unInput = new UniverseInput();
      InputMultiplexer multiplexer =
          new InputMultiplexer(
              scene,
              new GestureDetector(unitCtrl),
              new GestureDetector(camInput),
              new GestureDetector(uniInput),
              unitCtrl,
              camInput,
              uniInput,
              unInput);
      input.addProcessor(multiplexer);
      Inputs.add(camInput, uniInput);
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
      //Systems.DE = new DebugRender().init();
    }

    // 加载界面
    if (!assinited) {
      UI.Loading(assets.getProgress());
    } else {
      Draw.proj(camera);

      // 输入控制器（原 camInput / uniInput 的系统注册，改为集中驱动）
      Inputs.updateAll();

      // 游戏内每帧更新（手写系统 + 生成侧统一更新，见 Game.update）
      Game.update(Core.graphics.getDeltaTime() * 60f);

      Render.updateAll();
      camera.update();
    }
    scene.act();
    scene.draw();
    // Draw.flush();

    if (Systems.DE != null) {
      Draw.proj(UI.camera);
      Systems.DE.update();
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
