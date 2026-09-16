package caliniya.vergvoke.core;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.math.Rand;
import arc.util.Log;
import caliniya.vergvoke.Vergvoke;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.content.Blocks;

import caliniya.vergvoke.content.UnitTypes;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.io.DataIO;
import caliniya.vergvoke.io.GameIO;
import caliniya.vergvoke.map.Maps;
import caliniya.vergvoke.system.game.GameProcess;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.system.world.*;
import caliniya.vergvoke.type.Weapon;
import caliniya.vergvoke.system.*;
import java.util.*;

import static caliniya.vergvoke.game.data.WorldData.*;
import caliniya.vergvoke.world.stars.StarMap;
import caliniya.vergvoke.world.stars.StarNode;

public class Data {

  static {
    // Events.on(EventType.GameInit.class, evevt -> testinit());
  }

  // 从指定文件加载整个地图，使用异步
  // 这一步仅加载所有数据，理论上讲 不应该影响任何游戏界面
  public static void load(Fi file) {
    load(file, null);
  }

  /** 加载整个地图，加载并进入游戏后执行 onEnter（主线程）。 */
  public static void load(Fi file, Runnable onEnter) {
    GameIO.load(file, d -> DataIO.load(d, onEnter));
  }

  // 这个方法会加载所有的系统
  // 所有渲染方法 在此阶段不应该启动
  public static void loadSystems() {
    GameProcess.it = new GameProcess();
    Render.it = new Render();

    // 渲染器并入 Render 的管线数组（不再注册为独立系统）
    Render.renders.clear();
    Render.renders.add(new MapRender());
    Render.renders.add(new UnitRender());
    Render.renders.add(new BlockRender());
    Render.renders.add(new UniverseRender());
    // DebugRender.it = new DebugRender();

    BulletProcess.it = new BulletProcess();
    UnitMath.it = new UnitMath();
    EntityProces.it = new EntityProces();
  }

  // 初始化所有系统，允许其工作
  // 同时将游戏UI切换到游戏内部
  public static void enter() {
    // 手写主线程系统（过渡期）：GameProcess / Render
    if (GameProcess.it != null) GameProcess.it.init();
    if (Render.it != null) Render.it.init();

    // 渲染器也统一初始化（并入 Render 后不再走上面的系统列表）
    for (caliniya.vergvoke.system.System<?> render : Render.renders) {
      render.init();
    }

    BulletProcess.it.init();
    UnitMath.it.init();
    EntityProces.it.init();

    Game.starMap = new StarMap(2000, 2000);

    Rand r = new Rand();

    for (int i = 0; i < 100; ++i) {
      StarNode A, B;
      A = new StarNode(r.random(0, 2000), r.random(0, 2000), "呃啊" + r.random(0, 2000));
      B = new StarNode(r.random(0, 2000), r.random(0, 2000), "呃啊" + r.random(0, 2000));
      Game.starMap.addNode(A);
      Game.starMap.addNode(B);
      Game.starMap.link(A, B);
    }

    // Log.info(Render.universeCamera.position);
    // Log.info(Render.universeCamera.width + "  " + Render.universeCamera.height);
    // Log.info(Game.starMap.tree);
    /*
    Game.starMap.nodeSet.each(n->{
      Log.info(n.x +"   "+n.y);
    });*/

    // 标记进入游戏内：主循环开始驱动游戏更新（Game.update → 生成侧统一更新）
    Game.inGame = true;

    UI.Game();
  }
}
