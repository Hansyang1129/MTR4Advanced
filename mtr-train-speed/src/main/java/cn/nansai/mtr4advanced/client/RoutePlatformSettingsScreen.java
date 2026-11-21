package cn.nansai.mtr4advanced.client;

import cn.nansai.mtr4advanced.I18n;
import cn.nansai.mtr4advanced.api.RoutePlatformDoorAccess;
import cn.nansai.mtr4advanced.mixin.access.RoutePlatformDataSchemaAccess;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.MTRScreenBase;

/**
 * 「交路 × 站台」行为设置界面。
 *
 * <p>每条停靠当前站台的交路一行：前两列是<b>输入框</b>（延迟开门 / 提前关门的秒数，0 = 原版），
 * 第三列是「技术停车 是/否」按钮。</p>
 */
public class RoutePlatformSettingsScreen extends MTRScreenBase {

	private static final int NAME_WIDTH = IGui.SQUARE_SIZE * 8;
	private static final int FIELD_WIDTH = IGui.SQUARE_SIZE * 3;
	private static final int FIELD_GAP = 6;
	private static final int TECH_X = NAME_WIDTH + FIELD_WIDTH * 2 + FIELD_GAP * 2;
	private static final int ROW_Y = IGui.SQUARE_SIZE * 3;

	private final ObjectArrayList<Route> routes = new ObjectArrayList<>();
	private final ObjectArrayList<RoutePlatformData> stops = new ObjectArrayList<>();
	private final ObjectArrayList<Boolean> dirty = new ObjectArrayList<>();
	private final ObjectArrayList<TextFieldWidgetExtension> delayFields = new ObjectArrayList<>();
	private final ObjectArrayList<TextFieldWidgetExtension> earlyFields = new ObjectArrayList<>();
	private final ObjectArrayList<ButtonWidgetExtension> techButtons = new ObjectArrayList<>();

	public RoutePlatformSettingsScreen(Platform platform, ScreenExtension previousScreenExtension) {
		super(previousScreenExtension);
		final long platformId = platform.getId();
		final MinecraftClientData data = MinecraftClientData.getDashboardInstance() == null ? MinecraftClientData.getInstance() : MinecraftClientData.getDashboardInstance();
		for (Route route : data.routes) {
			for (RoutePlatformData routePlatformData : route.getRoutePlatforms()) {
				final long routePlatformId = ((RoutePlatformDataSchemaAccess) (Object) routePlatformData).mtr4a$getPlatformId();
				if (routePlatformId == platformId) {
					routes.add(route);
					stops.add(routePlatformData);
					dirty.add(false);
					break;
				}
			}
		}
	}

	@Override
	protected void init2() {
		super.init2();
		delayFields.clear();
		earlyFields.clear();
		techButtons.clear();
		for (int i = 0; i < routes.size(); i++) {
			final int index = i;
			final int y = ROW_Y + i * (IGui.SQUARE_SIZE + 4);

			final TextFieldWidgetExtension delayField = new TextFieldWidgetExtension(0, 0, FIELD_WIDTH, IGui.SQUARE_SIZE, 4, TextCase.DEFAULT, "\\D", "0");
			IDrawing.setPositionAndWidth(delayField, NAME_WIDTH, y, FIELD_WIDTH);
			delayField.setText2(String.valueOf(access(index).mtr4a$getDoorDelaySeconds()));
			((ScreenExtension) (Object) this).addChild(new ClickableWidget(delayField));

			final TextFieldWidgetExtension earlyField = new TextFieldWidgetExtension(0, 0, FIELD_WIDTH, IGui.SQUARE_SIZE, 4, TextCase.DEFAULT, "\\D", "0");
			IDrawing.setPositionAndWidth(earlyField, NAME_WIDTH + FIELD_WIDTH + FIELD_GAP, y, FIELD_WIDTH);
			earlyField.setText2(String.valueOf(access(index).mtr4a$getDoorCloseEarlySeconds()));
			((ScreenExtension) (Object) this).addChild(new ClickableWidget(earlyField));

			final ButtonWidgetExtension techButton = new ButtonWidgetExtension(0, 0, IGui.SQUARE_SIZE * 5, IGui.SQUARE_SIZE, technicalText(index), button -> {
				access(index).mtr4a$setTechnicalStop(!access(index).mtr4a$isTechnicalStop());
				markDirty(index);
				techButtons.get(index).setMessage2(Text.cast(technicalText(index)));
			});
			IDrawing.setPositionAndWidth(techButton, TECH_X, y, IGui.SQUARE_SIZE * 5);
			((ScreenExtension) (Object) this).addChild(new ClickableWidget(techButton));

			delayFields.add(delayField);
			earlyFields.add(earlyField);
			techButtons.add(techButton);
		}
	}

	@Override
	public void tick2() {
		super.tick2();
		for (TextFieldWidgetExtension field : delayFields) {
			field.tick2();
		}
		for (TextFieldWidgetExtension field : earlyFields) {
			field.tick2();
		}
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		// MTR 自己的屏幕都是自己画背景（模糊 + 遮罩），这里照做
		renderBackground(graphicsHolder);
		super.render(graphicsHolder, mouseX, mouseY, delta);
		graphicsHolder.drawText(I18n.text("mtr4a.gui.title_platform_route_settings"), IGui.SQUARE_SIZE, IGui.SQUARE_SIZE, IGui.ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(I18n.text("mtr4a.gui.column_route"), IGui.TEXT_PADDING, IGui.SQUARE_SIZE * 2, IGui.ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(I18n.text("mtr4a.gui.column_delay"), NAME_WIDTH, IGui.SQUARE_SIZE * 2, IGui.ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(I18n.text("mtr4a.gui.column_early"), NAME_WIDTH + FIELD_WIDTH + FIELD_GAP, IGui.SQUARE_SIZE * 2, IGui.ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(I18n.text("mtr4a.gui.column_technical"), TECH_X, IGui.SQUARE_SIZE * 2, IGui.ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		if (routes.isEmpty()) {
			graphicsHolder.drawText(I18n.text("mtr4a.gui.no_routes"), IGui.SQUARE_SIZE, ROW_Y, IGui.ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			return;
		}
		for (int i = 0; i < routes.size(); i++) {
			final int y = ROW_Y + i * (IGui.SQUARE_SIZE + 4);
			graphicsHolder.drawText(org.mtr.mapping.mapper.TextHelper.literal(routes.get(i).getName()), IGui.TEXT_PADDING, y + IGui.TEXT_PADDING, IGui.ARGB_BLACK + routes.get(i).getColor(), false, GraphicsHolder.getDefaultLight());
		}
	}

	@Override
	public void onClose2() {
		for (int i = 0; i < routes.size(); i++) {
			applyField(i, delayFields.get(i), true);
			applyField(i, earlyFields.get(i), false);
			if (Boolean.TRUE.equals(dirty.get(i))) {
				try {
					org.mtr.mod.InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getInstance()).addRoute(routes.get(i))));
				} catch (Throwable throwable) {
					System.out.println("[MTR4A] 交路站台设置提交失败: " + throwable);
				}
			}
		}
		super.onClose2();
	}

	private void applyField(int index, TextFieldWidgetExtension field, boolean isDelay) {
		try {
			final int value = Math.max(0, Math.min(3600, Integer.parseInt(field.getText2().trim())));
			final RoutePlatformDoorAccess access = access(index);
			if (isDelay) {
				if (value != access.mtr4a$getDoorDelaySeconds()) {
					access.mtr4a$setDoorDelaySeconds(value);
					markDirty(index);
				}
			} else if (value != access.mtr4a$getDoorCloseEarlySeconds()) {
				access.mtr4a$setDoorCloseEarlySeconds(value);
				markDirty(index);
			}
		} catch (Throwable ignored) {
			// 输入不是数字就保持原值
		}
	}

	private RoutePlatformDoorAccess access(int index) {
		return (RoutePlatformDoorAccess) (Object) stops.get(index);
	}

	private void markDirty(int index) {
		dirty.set(index, true);
	}

	private org.mtr.mapping.holder.MutableText technicalText(int index) {
		return I18n.text(access(index).mtr4a$isTechnicalStop() ? "mtr4a.gui.technical_stop_yes" : "mtr4a.gui.technical_stop_no");
	}
}
