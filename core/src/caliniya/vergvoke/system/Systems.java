package caliniya.vergvoke.system;

import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.system.game.*;
import caliniya.vergvoke.system.input.*;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.system.world.*;

public class Systems {

  public static Ar<caliniya.vergvoke.system.System> systems =
      new Ar<caliniya.vergvoke.system.System>(false);

  public static BulletProcess BP;
  public static UnitMath UM;
  public static EntityProces EP;
  public static Render R;
  public static GameProcess GP;
  public static DebugRender DE;

  // 渲染器（MapRender / UnitRender / BlockRender / UniverseRender）已并入 Render.renders，不在系统列表里

  public static void addSystem(caliniya.vergvoke.system.System<?>... newSystems) {
    for (caliniya.vergvoke.system.System<?> s : newSystems) {
      if (s != null && !systems.contains(s)) {
        systems.add(s);
      }
    }
    systems.sort();
  }
}
