package cn.nansai.mtr4advanced.mixin.access;

import org.mtr.core.generated.data.VehicleSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VehicleSchema.class)
public interface VehicleSchemaAccess {

    @Accessor("railProgress")
    double mtr4a$getRailProgress();

    /** 这一站已经停了多久（毫秒），车门逻辑用它判断延迟开门/提前关门 */
    @Accessor("elapsedDwellTime")
    long mtr4a$getElapsedDwellTime();
}
