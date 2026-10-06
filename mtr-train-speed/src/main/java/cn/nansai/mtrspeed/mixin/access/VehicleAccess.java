package cn.nansai.mtrspeed.mixin.access;

import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Vehicle.class)
public interface VehicleAccess {

    @Accessor("siding")
    Siding mtrspeed$getSiding();

    @Accessor("deviationSpeedAdjustment")
    double mtrspeed$getDeviationSpeedAdjustment();
}
