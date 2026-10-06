package cn.nansai.mtrspeed.mixin;

import cn.nansai.mtrspeed.api.SidingMaxSpeedAccess;
import org.mtr.core.data.Siding;
import org.mtr.core.generated.data.SidingSchema;
import org.mtr.core.serializer.WriterBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把「列车最高时速」写进侧线的序列化输出。
 *
 * 注入点选 SidingSchema#serializeData 而不是 Siding#serializeData：
 * serializeData 真正声明在 SidingSchema 上，Siding 只是继承，
 * Mixin 不会为目标类凭空生成父类方法的 override。
 * Siding#serializeFullData 会 super -> SerializedDataBase#serializeFullData -> this.serializeData，
 * 所以走存档（full）和走网络（writeDataset / getJsonObjectFromData）都会经过这里。
 */
@Mixin(SidingSchema.class)
public class SidingSchemaMixin {

    @Inject(method = "serializeData", at = @At("TAIL"))
    private void mtrspeed$serializeData(WriterBase writerBase, CallbackInfo callbackInfo) {
        if ((Object) this instanceof Siding) {
            final SidingMaxSpeedAccess access = (SidingMaxSpeedAccess) (Object) this;
            writerBase.writeDouble("trainMaxSpeedKilometersPerHour", access.mtrspeed$getTrainMaxSpeedKilometersPerHour());
            writerBase.writeBoolean("maxSpeedFollowsDeviation", access.mtrspeed$getMaxSpeedFollowsDeviation());
        }
    }
}
