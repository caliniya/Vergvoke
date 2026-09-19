package caliniya.vergvoke.system.render;

import arc.Core;
import arc.Events;
import arc.graphics.Camera;
import arc.graphics.g2d.Draw;
import arc.math.geom.Rect;
import arc.util.Log;
import caliniya.vergvoke.base.shaders.SpaceShader;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.core.Render;
import caliniya.vergvoke.game.Game;
import caliniya.vergvoke.system.System;

/** 宇宙渲染 */
public class UniverseRender extends System<UniverseRender> {

  /** 太空背景着色器 */
  private SpaceShader background;

  @Override
  public UniverseRender init() {
    this.index = 16;
    background = new SpaceShader(Render.universeCamera, () -> Render.universeZoom);
    background.parallaxScale = 0.5f;
    background.baseScale = 0.6f;
    Events.run(EventType.events.EnterUV, () -> paused = false);
    Events.run(EventType.events.ExitUV, () -> paused = true);
    paused = true;
    return super.init(false, false);
  }

  @Override
  public void update(float delta) {
    if (!inited || paused) return;
    Camera cam = Render.universeCamera;
    float zoom = Render.universeZoom;
    cam.update();
    background.render();
    Draw.proj(cam);
    Game.starMap.draw(cam);
    //Game.starMap.roadSet.each(r -> r.draw());
    Draw.proj(Core.camera);
  }

  @Override
  public void dispose() {
    background.dispose();
    super.dispose();
  }
}
