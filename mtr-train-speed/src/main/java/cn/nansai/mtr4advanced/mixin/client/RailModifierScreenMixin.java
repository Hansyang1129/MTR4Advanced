package cn.nansai.mtr4advanced.mixin.client;

import cn.nansai.mtr4advanced.I18n;
import cn.nansai.mtr4advanced.RailSpeedLevels;
import org.mtr.core.data.Rail;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.RailModifierScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 MTR 自己的「轨道设定」界面里加一行「限速」下拉菜单（形状 · 样式 · 半径 那个界面）。
 *
 * <p>点「限速 xx km/h ▾」会在下方展开 10 个原版档位（20…300 km/h），点哪个就是哪个；
 * 选完用重建 Rail 的方式提交（形状/半径/样式/单双向都保留），不重铺轨道。</p>
 *
 * <p><b>注意</b>：所有状态都在 {@code mtr4a$init} 里惰性创建，不依赖 mixin 字段初始化器
 * （实测初始化器不会进入目标类构造器，字段会是 null）。整个 handler 包了 try/catch，
 * 出问题最多是控件不显示，不会把游戏崩掉。</p>
 */
@Mixin(RailModifierScreen.class)
public class RailModifierScreenMixin {

	@Unique
	private static final int MTR4A_PANEL_WIDTH = IGui.SQUARE_SIZE * 6;

	@Unique
	private static final int MTR4A_OFFSCREEN = -1000;

	@Shadow
	private Rail rail;

	@Shadow
	private int xStart;

	@Unique
	private ButtonWidgetExtension mtr4a$buttonToggle;

	@Unique
	private ButtonWidgetExtension[] mtr4a$buttons;

	@Unique
	private MutableText mtr4a$labelSpeed;

	@Unique
	private MutableText mtr4a$labelFixed;

	@Unique
	private int mtr4a$selectedLevel;

	@Unique
	private int mtr4a$rowY;

	@Unique
	private boolean mtr4a$open;

	@Unique
	private boolean mtr4a$ready;

	@Inject(method = "init2", at = @At("TAIL"))
	private void mtr4a$init(CallbackInfo callbackInfo) {
		try {
			if (rail == null) {
				return;
			}
			mtr4a$rowY = IGui.SQUARE_SIZE * 2 + IGui.TEXT_FIELD_PADDING;
			mtr4a$selectedLevel = (int) Math.max(rail.getSpeedLimitKilometersPerHour(false), rail.getSpeedLimitKilometersPerHour(true));
			if (!mtr4a$canEdit()) {
				return;
			}
			mtr4a$ensure();
			mtr4a$open = false;
			mtr4a$refresh();
			mtr4a$layout();
		} catch (Throwable throwable) {
			System.out.println("[MTR4A] 限速控件初始化失败（不影响游戏）: " + throwable);
		}
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void mtr4a$renderBackground(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo callbackInfo) {
		try {
			if (mtr4a$open && mtr4a$ready) {
				final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
				guiDrawing.beginDrawingRectangle();
				guiDrawing.drawRectangle(xStart - 2, mtr4a$panelTop() - 2, xStart + MTR4A_PANEL_WIDTH + 2, mtr4a$panelTop() + RailSpeedLevels.LEVELS.length * IGui.SQUARE_SIZE + 2, 0xE0101010);
				guiDrawing.finishDrawingRectangle();
			}
		} catch (Throwable throwable) {
			System.out.println("[MTR4A] 限速下拉背景绘制失败: " + throwable);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void mtr4a$renderLabel(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo callbackInfo) {
		try {
			if (rail == null) {
				return;
			}
			graphicsHolder.drawText(mtr4a$canEdit() ? mtr4a$labelSpeed : mtr4a$labelFixed, IGui.TEXT_PADDING, mtr4a$rowY + IGui.TEXT_PADDING, IGui.ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		} catch (Throwable throwable) {
			System.out.println("[MTR4A] 限速标签绘制失败: " + throwable);
		}
	}

	/** 惰性创建全部控件（mixin 字段初始化器不可靠，必须在这里建） */
	@Unique
	private void mtr4a$ensure() {
		if (mtr4a$ready) {
			return;
		}
		if (mtr4a$labelSpeed == null) {
			mtr4a$labelSpeed = I18n.text("mtr4a.gui.rail_speed_limit");
		}
		if (mtr4a$labelFixed == null) {
			mtr4a$labelFixed = I18n.text("mtr4a.gui.rail_speed_fixed");
		}
		if (mtr4a$buttonToggle == null) {
			mtr4a$buttonToggle = new ButtonWidgetExtension(0, 0, MTR4A_PANEL_WIDTH, IGui.SQUARE_SIZE, mtr4a$toggleText(), button -> {
				mtr4a$open = !mtr4a$open;
				mtr4a$layout();
			});
			IDrawing.setPositionAndWidth(mtr4a$buttonToggle, xStart, mtr4a$rowY, MTR4A_PANEL_WIDTH);
			((ScreenExtension) (Object) this).addChild(new ClickableWidget(mtr4a$buttonToggle));
		}
		if (mtr4a$buttons == null) {
			mtr4a$buttons = new ButtonWidgetExtension[RailSpeedLevels.LEVELS.length];
			for (int i = 0; i < RailSpeedLevels.LEVELS.length; i++) {
				final int level = RailSpeedLevels.LEVELS[i];
				mtr4a$buttons[i] = new ButtonWidgetExtension(0, 0, MTR4A_PANEL_WIDTH, IGui.SQUARE_SIZE, TextHelper.literal(String.valueOf(level)), button -> mtr4a$apply(level));
				IDrawing.setPositionAndWidth(mtr4a$buttons[i], xStart, MTR4A_OFFSCREEN, MTR4A_PANEL_WIDTH);
				((ScreenExtension) (Object) this).addChild(new ClickableWidget(mtr4a$buttons[i]));
			}
		}
		mtr4a$ready = true;
	}

	@Unique
	private int mtr4a$panelTop() {
		return mtr4a$rowY + IGui.SQUARE_SIZE + 2;
	}

	@Unique
	private boolean mtr4a$canEdit() {
		return !rail.isPlatform() && !rail.isSiding() && !rail.canTurnBack();
	}

	@Unique
	private MutableText mtr4a$toggleText() {
		return I18n.text("mtr4a.gui.rail_speed_open", mtr4a$selectedLevel);
	}

	@Unique
	private void mtr4a$layout() {
		if (mtr4a$buttonToggle != null) {
			mtr4a$buttonToggle.setMessage2(Text.cast(mtr4a$toggleText()));
		}
		if (mtr4a$buttons != null) {
			for (int i = 0; i < mtr4a$buttons.length; i++) {
				final int y = mtr4a$open ? mtr4a$panelTop() + i * IGui.SQUARE_SIZE : MTR4A_OFFSCREEN;
				IDrawing.setPositionAndWidth(mtr4a$buttons[i], xStart, y, MTR4A_PANEL_WIDTH);
			}
		}
	}

	@Unique
	private void mtr4a$refresh() {
		if (mtr4a$buttons == null) {
			return;
		}
		for (int i = 0; i < mtr4a$buttons.length; i++) {
			final int level = RailSpeedLevels.LEVELS[i];
			mtr4a$buttons[i].setMessage2(Text.cast(TextHelper.literal(level == mtr4a$selectedLevel ? "▶ " + level : String.valueOf(level))));
		}
	}

	@Unique
	private void mtr4a$apply(int level) {
		try {
			if (!mtr4a$canEdit()) {
				return;
			}
			mtr4a$open = false;
			mtr4a$selectedLevel = level;
			mtr4a$refresh();
			mtr4a$layout();

			final boolean oneWay = rail.getSpeedLimitKilometersPerHour(false) == 0;
			RailSpeedLevels.set(level, oneWay);
			try {
				final Rail newRail = Rail.copy(rail, rail.railMath.getShape(), rail.railMath.getVerticalRadius());
				org.mtr.mod.InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getInstance()).addRail(newRail)));
			} finally {
				RailSpeedLevels.clear();
			}
		} catch (Throwable throwable) {
			System.out.println("[MTR4A] 改限速失败（不影响游戏）: " + throwable);
		}
	}
}
