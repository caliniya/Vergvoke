package caliniya.vergvoke.core.meta.stat;

import arc.Core;

public enum Stat {
    Name("name"),
    info("info"),

    health("health"),
    armor("armor"),
    shield("shield"),
    heat("heat"),
    energy("energy"),
    power("power"),

    // 基础
    healthMax("healthMax", StatType.general),
    speed("speed", StatType.general, StatUnit.tilesSecond),
    rotateSpeed("rotateSpeed", StatType.general, StatUnit.degreesSecond),
    energyMax("energyMax", StatType.general),
    energyRegen("energyRegen", StatType.general, StatUnit.perSecond),
    // 防护
    armorMax("armorMax", StatType.protect),
    armorValue("armorValue", StatType.protect),
    // 支持
    shieldMax("shieldMax", StatType.function),
    shieldStrength("shieldStrength", StatType.function, StatUnit.percent),
    shieldRegen("shieldRegen", StatType.function, StatUnit.perSecond),
    heatMax("heatMax", StatType.function),
    shieldCost("shieldCost", StatType.function),
    radius("radius", StatType.function, StatUnit.blocks),
    heatSpeed("heatSpeed", StatType.function, StatUnit.perSecond),
    heatPerShot("heatPerShot", StatType.function, StatUnit.perShot);

    public final String name, localizedName;
    public final StatType type;
    public final StatUnit unit;

    Stat(String name) {
        this(name, StatType.none);
    }

    // 表示某一种统计信息，例如生命
    Stat(String name, StatType type) {
        this(name, type, StatUnit.none);
    }

    Stat(String name, StatType type, StatUnit unit) {
        this.name = name;
        this.type = type;
        this.unit = unit;
        this.localizedName = Core.bundle.get("stat." + name);
    }

    @Override
    public String toString() {
        return "Stat." + this.name;
    }
}
