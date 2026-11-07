package cn.nansai.mtr4advanced.mixin.access;

import org.mtr.core.generated.data.VehicleSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VehicleSchema.class)
public interface VehicleSchemaAccess {

    @Accessor("railProgress")
    double mtr4a$getRailProgress();
}
