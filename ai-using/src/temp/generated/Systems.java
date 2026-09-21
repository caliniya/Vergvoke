package caliniya.vergvoke.base.ecs;

import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.system.System;
import caliniya.vergvoke.system.game.GameProcess;
import java.lang.Class;
import java.lang.String;

/**
 * 由注解处理器生成：系统总览。
 * @SystemDef 的线程视图（Class 清单）＋ 组件系统数组与统一更新入口（update / updateAll）。
 */
public final class Systems {
  /**
   * 线程 {@code main} 的 @SystemDef 类（按 index 升序）。
   */
  public static final Class<?>[] main = { GameProcess.class };

  /**
   * 线程清单（与上面的字段一一对应）。
   */
  public static final String[] THREADS = { "main" };

  /**
   * 全部组件系统（按 @SystemDef.index 升序）。
   */
  public static final Ar<System<?>> systems = new Ar<>();

  private Systems() {
  }

  /**
   * 按 @SystemDef.index 依次调用所有组件系统。
   */
  public static void update(float delta) {
    for (System<?> sys : systems) {
      sys.update(delta);
    }
  }

  /**
   * 按划定的帧顺序总驱动：基础更新 → 组件系统 → 实体更新（帧时间倍率统一传入）。
   */
  public static void updateAll(float delta) {
    // 基础更新（热量 / 能量 + 能力 / 模组）
    for (Unit e : EntityArs.Unit) {
      e.sync(delta);
    }
    // 组件系统（按 @SystemDef.index）
    update(delta);
    // 实体自身更新（轻量档注入在 update(float) 里）
    for (Unit e : EntityArs.Unit) {
      e.update(delta);
    }
  }
}
