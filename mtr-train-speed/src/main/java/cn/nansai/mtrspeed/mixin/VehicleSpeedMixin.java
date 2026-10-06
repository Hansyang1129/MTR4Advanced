package cn.nansai.mtrspeed.mixin;

import cn.nansai.mtrspeed.SpeedLogic;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 覆盖 Vehicle#simulateMoving 里的两个取值：
 *
 * 1. pathData.getSpeedLimitMetersPerMillisecond()
 *    —— 原版用「车头所在轨段」的限速，改成「列车占用区间内的最低限速」-> 提速由车尾控制。
 *
 * 2. Siding#getUpcomingSlowerSpeed(...)
 *    —— 原版就是基于车头的前瞻制动曲线，保留不动，只额外套上列车最高时速 -> 降速由车头控制。
 */
@Mixin(Vehicle.class)
public class VehicleSpeedMixin {

    @Redirect(
            method = "simulateMoving",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D")
    )
    private double mtrspeed$railSpeedLimit(PathData pathData) {
        return SpeedLogic.getAccelerationTarget((Vehicle) (Object) this, pathData.getSpeedLimitMetersPerMillisecond());
    }

    @Redirect(
            method = "simulateMoving",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/core/data/Siding;getUpcomingSlowerSpeed(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;IDDD)D"
            )
    )
    private double mtrspeed$upcomingSlowerSpeed(
            ObjectList<PathData> path, int currentIndex, double railProgress, double currentSpeed, double deceleration
    ) {
        final double original = Siding.getUpcomingSlowerSpeed(path, currentIndex, railProgress, currentSpeed, deceleration);
        return SpeedLogic.getDecelerationTarget((Vehicle) (Object) this, original);
    }
}
