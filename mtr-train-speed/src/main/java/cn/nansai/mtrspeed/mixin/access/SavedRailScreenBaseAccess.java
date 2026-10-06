package cn.nansai.mtrspeed.mixin.access;

import org.mtr.core.data.SavedRailBase;
import org.mtr.mod.screen.SavedRailScreenBase;
import org.mtr.mod.screen.WidgetShorterSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SavedRailScreenBase.class)
public interface SavedRailScreenBaseAccess {

    @Accessor("savedRailBase")
    SavedRailBase mtrspeed$getSavedRailBase();

    @Accessor("showScheduleControls")
    boolean mtrspeed$showScheduleControls();

    @Accessor("textWidth")
    int mtrspeed$getTextWidth();

    /** 布局锚点：这是 MTR 自己最后一行控件，本模组的新行全部排在它下面 */
    @Accessor("sliderDwellTimeSec")
    WidgetShorterSlider mtrspeed$getSliderDwellTimeSec();
}
