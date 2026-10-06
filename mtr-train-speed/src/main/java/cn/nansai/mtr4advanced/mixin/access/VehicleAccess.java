package cn.nansai.mtr4advanced.mixin.access;

import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Vehicle.class)
public interface VehicleAccess {

    @Accessor("siding")
    Siding mtr4a$getSiding();

    @Accessor("deviationSpeedAdjustment")
    double mtr4a$getDeviationSpeedAdjustment();
}
