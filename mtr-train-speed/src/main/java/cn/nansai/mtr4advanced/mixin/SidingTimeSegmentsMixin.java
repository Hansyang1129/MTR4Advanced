package cn.nansai.mtr4advanced.mixin;

import cn.nansai.mtr4advanced.Config;
import cn.nansai.mtr4advanced.SpeedLogic;
import cn.nansai.mtr4advanced.api.SidingMaxSpeedAccess;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让侧线的时刻表推算（Siding#generatePathDistancesAndTimeSegments）也走现实逻辑：
 *
 * 1. 套用「列车最高时速」——否则线路 120、列车 80 时，计划按 120 算，列车永远显示晚点。
 * 2. 按【这条侧线自己的列车长度】做「车尾控制提速」——
 *    原版时刻表是质点模型（只看车头那一点的限速），而运行时是「整列车通过才提速」，
 *    两者不一致 => 列车结构性晚点。这里在推算时就用
 *    [railProgress - 列车总长, railProgress] 占用区间内的最低限速，
 *    把车尾延迟带来的耗时算进计划时间。
 *
 * 坐标说明：MTR 里 railProgress 是【车头】沿路径的里程
 * （时刻表初值 = (railLength + 车长) / 2，即列车居中停在侧线上时的车头位置；
 *  运行时 Vehicle#simulateInDepot 也是 railProgress = vehicleExtraData.getDefaultPosition()）。
 * 所以占用区间 = [railProgress - 车长, railProgress]，与 SpeedLogic 运行时口径一致。
 */
@Mixin(Siding.class)
public class SidingTimeSegmentsMixin {

    /** 本次循环捕获的完整路径（从 getUpcomingSlowerSpeed 的入参拿到，局部变量拿不到） */
    @Unique
    private ObjectList<PathData> mtr4a$capturedPath;

    /** 本次循环的车头里程（局部变量，只能从 getUpcomingSlowerSpeed 的入参拿到） */
    @Unique
    private double mtr4a$capturedRailProgress = Double.NaN;

    /**
     * 这条侧线的列车总长（米）。
     * 每次重算路径时车型可能变，所以跟着 capturedPath 一起刷新，不要做成进程级缓存；
     * 但也不能在每个轨段的回调里现算（原版在方法开头只算一次）。
     */
    @Unique
    private double mtr4a$capturedVehicleLength = -1;

    @Unique
    private double mtr4a$maxSpeedMetersPerMillisecond() {
        Config.load();
        if (!Config.applyMaxSpeedToTimeSegments) {
            return Double.MAX_VALUE;
        }
        double kilometersPerHour = ((SidingMaxSpeedAccess) this).mtr4a$getTrainMaxSpeedKilometersPerHour();
        if (kilometersPerHour <= 0) {
            kilometersPerHour = Config.defaultMaxSpeedKilometersPerHour;
        }
        return kilometersPerHour > 0 ? SpeedLogic.kilometersPerHourToMetersPerMillisecond(kilometersPerHour) : Double.MAX_VALUE;
    }

    /**
     * 先于 getSpeedLimitMetersPerMillisecond 执行（同一轮循环里 777 -> 819），
     * 借这次调用把 path 和 railProgress 抓下来。
     */
    @Redirect(
            method = "generatePathDistancesAndTimeSegments",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/Siding;getUpcomingSlowerSpeed(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;IDDD)D"
            )
    )
    private double mtr4a$captureAndCapUpcomingSlowerSpeed(
            ObjectList<PathData> path, int currentIndex, double railProgress, double currentSpeed, double deceleration
    ) {
        mtr4a$capturedPath = path;
        mtr4a$capturedRailProgress = railProgress;
        mtr4a$capturedVehicleLength = Siding.getTotalVehicleLength(((Siding) (Object) this).getVehicleCars());
        final double original = Siding.getUpcomingSlowerSpeed(path, currentIndex, railProgress, currentSpeed, deceleration);
        return original < 0 ? original : Math.min(original, mtr4a$maxSpeedMetersPerMillisecond());
    }

    /**
     * 轨段限速：先按「整列车占用区间内的最低限速」压一遍（车尾控制提速），再套列车最高时速。
     */
    @Redirect(
            method = "generatePathDistancesAndTimeSegments",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D")
    )
    private double mtr4a$capRailSpeedLimit(PathData pathData) {
        Config.load();
        double limit = pathData.getSpeedLimitMetersPerMillisecond();

        if (Config.applyVehicleLengthToTimeSegments && mtr4a$capturedPath != null && !Double.isNaN(mtr4a$capturedRailProgress)) {
            final double vehicleLength = mtr4a$capturedVehicleLength;
            if (vehicleLength > 0) {
                final double head = mtr4a$capturedRailProgress;
                limit = Math.min(limit, SpeedLogic.occupiedMinimumSpeedLimit(mtr4a$capturedPath, head - vehicleLength, head, limit));
            }
        }

        return Math.min(limit, mtr4a$maxSpeedMetersPerMillisecond());
    }
}
