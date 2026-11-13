package pl.holovis;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import pl.holovis.gui.HoloScreen;

public class HoloVisClient implements ClientModInitializer {
    public static KeyBinding OPEN_MENU, SP_NEXT, SP_PREV, SP_TOGGLE;
    private static LivingEntity lastTarget;
    private static int lastTargetTicks;

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, code, "key.categories.holovis"));
    }

    /** Click Sound: kliknięcie przycisku w dowolnym menu (np. ESC -> Options). */
    private static boolean onScreenClick(Screen screen, double mx, double my, int button) {
        HoloConfig cfg = HoloConfig.get();
        if (cfg.clickSound.enabled && button == 0) {
            for (Element el : screen.children()) {
                if (el instanceof ClickableWidget w && w.visible && w.active && w.isMouseOver(mx, my)) {
                    HoloSounds.play(cfg.clickSound);
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public void onInitializeClient() {
        HoloConfig.load();
        HoloSounds.refresh();
        Spotify.sync();

        OPEN_MENU = key("key.holovis.open", GLFW.GLFW_KEY_RIGHT_SHIFT);
        SP_NEXT = key("key.holovis.spotify_next", GLFW.GLFW_KEY_RIGHT_BRACKET);
        SP_PREV = key("key.holovis.spotify_prev", GLFW.GLFW_KEY_LEFT_BRACKET);
        SP_TOGGLE = key("key.holovis.spotify_toggle", GLFW.GLFW_KEY_BACKSLASH);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_MENU.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new HoloScreen());
            }
            while (SP_NEXT.wasPressed()) Spotify.next();
            while (SP_PREV.wasPressed()) Spotify.previous();
            while (SP_TOGGLE.wasPressed()) Spotify.toggle();

            if (lastTarget != null) {
                lastTargetTicks++;
                if (!lastTarget.isAlive()) {
                    HoloConfig cfg = HoloConfig.get();
                    if (cfg.killSound.enabled) HoloSounds.play(cfg.killSound);
                    lastTarget = null;
                } else if (lastTargetTicks > 100) {
                    lastTarget = null;
                }
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && entity instanceof LivingEntity living) {
                HoloConfig cfg = HoloConfig.get();
                if (cfg.hitSound.enabled) HoloSounds.play(cfg.hitSound);
                lastTarget = living;
                lastTargetTicks = 0;
            }
            return ActionResult.PASS;
        });

        // Click Sound: machnięcie ręką w powietrze (bez trafienia gracza/moba/bloku)
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            HoloConfig cfg = HoloConfig.get();
            if (clickCount > 0 && cfg.clickSound.enabled && client.currentScreen == null
                    && (client.crosshairTarget == null || client.crosshairTarget.getType() == HitResult.Type.MISS)) {
                HoloSounds.play(cfg.clickSound);
            }
            return false;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenMouseEvents.allowMouseClick(screen).register(HoloVisClient::onScreenClick);
            if (screen instanceof ChatScreen) ScreenEvents.afterRender(screen).register(SpotifyOverlay::render);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(HitboxRenderer::render);
        HudRenderCallback.EVENT.register(HudRenderer::render);
    }
}
