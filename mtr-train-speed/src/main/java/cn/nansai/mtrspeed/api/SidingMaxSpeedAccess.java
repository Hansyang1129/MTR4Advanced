package cn.nansai.mtrspeed.api;

/**
 * 侧线（Siding）上的「列车最高时速」字段访问器。
 *
 * 注意：这个接口必须放在 mixin 包【之外】。
 * Mixin 会把 mixin 配置里 package 指向的包（这里是 cn.nansai.mtrspeed.mixin.*）
 * 下的所有类都当成 mixin 处理，普通类放在里面会在运行时抛
 * IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly
 */
public interface SidingMaxSpeedAccess {

    /** @return km/h，0 表示不限制 */
    double mtrspeed$getTrainMaxSpeedKilometersPerHour();

    void mtrspeed$setTrainMaxSpeedKilometersPerHour(double kilometersPerHour);

    /** 最高时速是否随晚点增益（deviationSpeedAdjustment）一起上浮 */
    boolean mtrspeed$getMaxSpeedFollowsDeviation();

    void mtrspeed$setMaxSpeedFollowsDeviation(boolean value);
}
