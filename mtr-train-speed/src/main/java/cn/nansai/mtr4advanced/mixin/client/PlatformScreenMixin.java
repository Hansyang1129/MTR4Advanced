package cn.nansai.mtr4advanced.mixin.client;

import cn.nansai.mtr4advanced.I18n;
import cn.nansai.mtr4advanced.client.RoutePlatformSettingsScreen;
import cn.nansai.mtr4advanced.mixin.access.SavedRailScreenBaseAccess;
import org.mtr.core.data.Platform;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.PlatformScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在站台线设置界面里加一个「交路站台行为」按钮（打开按交路区分的设置屏）。 */
@Mixin(PlatformScreen.class)
public class PlatformScreenMixin {

	@Inject(method = "init2", at = @At("TAIL"))
	private void mtr4a$init(CallbackInfo callbackInfo) {
		try {
			final org.mtr.core.data.SavedRailBase savedRailBase = ((SavedRailScreenBaseAccess) (Object) this).mtr4a$getSavedRailBase();
			if (!(savedRailBase instanceof Platform)) {
				return;
			}
			final Platform platform = (Platform) savedRailBase;
			final ButtonWidgetExtension button = new ButtonWidgetExtension(0, 0, IGui.SQUARE_SIZE * 9, IGui.SQUARE_SIZE, I18n.text("mtr4a.gui.platform_route_settings"), b -> MinecraftClient.getInstance().openScreen(new Screen(new RoutePlatformSettingsScreen(platform, (ScreenExtension) (Object) this))));
			// 放在「途径本站台之路线」列表下面，避开停留时间的滑条与刻度
			IDrawing.setPositionAndWidth(button, IGui.SQUARE_SIZE, IGui.SQUARE_SIZE * 6, IGui.SQUARE_SIZE * 9);
			((ScreenExtension) (Object) this).addChild(new ClickableWidget(button));
		} catch (Throwable throwable) {
			System.out.println("[MTR4A] 站台交路按钮初始化失败（不影响游戏）: " + throwable);
		}
	}
}
