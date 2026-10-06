package cn.nansai.mtrspeed.mixin;

import cn.nansai.mtrspeed.api.SidingMaxSpeedAccess;
import org.mtr.core.data.Siding;
import org.mtr.core.serializer.ReaderBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给侧线加一个「列车最高时速」字段。
 *
 * 读取：注入 Siding#updateData（Siding 自己声明了这个方法）。
 * 写出：不能注入 Siding#serializeData —— 它是从 SidingSchema 继承来的，
 * Mixin 0.8.7 不会为目标类生成父类方法的 override，会报
 * "could not find any targets matching 'serializeData' in org/mtr/core/data/Siding"。
 * 所以写出挪到 {@link SidingSchemaMixin}，注入 SidingSchema#serializeData。
 */
@Mixin(Siding.class)
public class SidingMixin implements SidingMaxSpeedAccess {

    @Unique
    private double mtrspeed$trainMaxSpeedKilometersPerHour;

    @Unique
    private boolean mtrspeed$maxSpeedFollowsDeviation;

    @Override
    public double mtrspeed$getTrainMaxSpeedKilometersPerHour() {
        return mtrspeed$trainMaxSpeedKilometersPerHour;
    }

    @Override
    public void mtrspeed$setTrainMaxSpeedKilometersPerHour(double kilometersPerHour) {
        // Math.max(0, NaN) 会返回 NaN，必须显式挡掉，否则 NaN 会一路透传到限速计算里
        if (Double.isNaN(kilometersPerHour) || Double.isInfinite(kilometersPerHour)) {
            return;
        }
        mtrspeed$trainMaxSpeedKilometersPerHour = Math.max(0, kilometersPerHour);
    }

    @Override
    public boolean mtrspeed$getMaxSpeedFollowsDeviation() {
        return mtrspeed$maxSpeedFollowsDeviation;
    }

    @Override
    public void mtrspeed$setMaxSpeedFollowsDeviation(boolean value) {
        mtrspeed$maxSpeedFollowsDeviation = value;
    }

    @Inject(method = "updateData", at = @At("TAIL"))
    private void mtrspeed$updateData(ReaderBase readerBase, CallbackInfo callbackInfo) {
        readerBase.unpackDouble("trainMaxSpeedKilometersPerHour", value -> mtrspeed$trainMaxSpeedKilometersPerHour = value);
        readerBase.unpackBoolean("maxSpeedFollowsDeviation", value -> mtrspeed$maxSpeedFollowsDeviation = value);
    }
}
