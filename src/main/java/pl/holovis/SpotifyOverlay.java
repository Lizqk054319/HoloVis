package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.HudRenderer.Element;

import java.util.List;

/** Po otwarciu czatu i najechaniu na element Spotify: tekst piosenki + suwak przewijania + przyciski. */
public final class SpotifyOverlay {
    private SpotifyOverlay() {}

    private static final int W = 244, H = 158;
    private static boolean wasDown, dragging;
    private static float dragRatio;

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static void render(Screen screen, DrawContext c, int mx, int my, float delta) {
        HoloConfig cfg = HoloConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!cfg.spotify) { wasDown = down; dragging = false; return; }

        int sw = screen.width, sh = screen.height;
        int[] ep = HudRenderer.pixelPos(Element.SPOTIFY, sw, sh);
        int[] es = HudRenderer.size(Element.SPOTIFY);
        int px = MathHelper.clamp(ep[0], 4, Math.max(4, sw - W - 4));
        int py = ep[1] + es[1];
        if (py + H > sh - 4) py = ep[1] - H;

        boolean over = in(mx, my, ep[0], ep[1], es[0], es[1]) || in(mx, my, px, py, W, H);
        if (!over && !dragging) { wasDown = down; return; }

        TextRenderer tr = mc.textRenderer;
        int accent = cfg.guiColor.rgb();
        int accent2 = cfg.guiGradient ? cfg.guiColor2.rgb() : accent;
        int txt = 0xFFE8EEF8, gray = 0xFF8791A6;

        // tło
        c.fill(px, py, px + W, py + H, 0xEE0B1020);
        c.drawBorder(px, py, W, H, 0xAA000000 | accent);
        c.fillGradient(px, py, px + W, py + 2, 0xFF000000 | accent, 0xFF000000 | accent2);

        // tytuł
        String head = Spotify.running ? (Spotify.track.isEmpty() ? "Spotify" : Spotify.track) : "Nie wykryto Spotify";
        c.drawText(tr, tr.trimToWidth(head, W - 20), px + 10, py + 8, txt, false);

        // tekst piosenki
        int ly = py + 24;
        int lines = 7, lh = 11;
        if (!Spotify.running) {
            centered(c, tr, "Uruchom Spotify na komputerze", px, ly + 3 * lh, gray);
        } else {
            int st = Lyrics.state;
            List<Lyrics.Line> l = Lyrics.lines;
            if (st == 1) centered(c, tr, "Wczytywanie tekstu...", px, ly + 3 * lh, gray);
            else if (st == 4 || st == 0 || l.isEmpty()) centered(c, tr, "Brak tekstu dla tego utworu", px, ly + 3 * lh, gray);
            else if (st == 2) {
                int cur = Lyrics.indexAt(Spotify.position());
                for (int i = 0; i < lines; i++) {
                    int idx = cur - 3 + i;
                    if (idx < 0 || idx >= l.size()) continue;
                    boolean now = idx == cur;
                    int dist = Math.abs(idx - cur);
                    int a = now ? 255 : Math.max(70, 200 - dist * 50);
                    int col = now ? (0xFF000000 | accent) : ((a << 24) | 0x9AA4B8);
                    String t = tr.trimToWidth(l.get(idx).text(), W - 20);
                    centered(c, tr, t, px, ly + i * lh, col);
                }
            } else {
                for (int i = 0; i < lines && i < l.size(); i++)
                    centered(c, tr, tr.trimToWidth(l.get(i).text(), W - 20), px, ly + i * lh, 0xFFB8C0D0);
            }
        }

        // suwak
        int bx = px + 10, bw = W - 20, by = py + H - 44;
        boolean canSeek = Spotify.running && Spotify.hasTimeline && Spotify.durMs > 0;
        if (canSeek) {
            float ratio = dragging ? dragRatio : MathHelper.clamp(Spotify.position() / (float) Spotify.durMs, 0f, 1f);
            c.fill(bx, by, bx + bw, by + 4, 0xFF2A3246);
            int fw = (int) (bw * ratio);
            c.fillGradient(bx, by, bx + Math.max(1, fw), by + 4, 0xFF000000 | accent, 0xFF000000 | accent2);
            int kx = bx + (int) (ratio * (bw - 4));
            c.fill(kx, by - 3, kx + 4, by + 7, 0xFFFFFFFF);
            long shown = dragging ? (long) (dragRatio * Spotify.durMs) : Spotify.position();
            c.drawText(tr, time(shown), bx, by + 10, gray, false);
            String end = time(Spotify.durMs);
            c.drawText(tr, end, bx + bw - tr.getWidth(end), by + 10, gray, false);

            if (down && !wasDown && in(mx, my, bx - 3, by - 6, bw + 6, 16)) dragging = true;
            if (dragging) {
                dragRatio = MathHelper.clamp((mx - bx) / (float) bw, 0f, 1f);
                if (!down) {
                    Spotify.seek((long) (dragRatio * Spotify.durMs));
                    dragging = false;
                }
            }
        } else {
            dragging = false;
            c.drawText(tr, "Przewijanie: tylko Windows 10/11", bx, by, gray, false);
        }

        // przyciski
        String[] labels = {"Poprz.", Spotify.playing ? "Pauza" : "Graj", "Dalej"};
        int bwid = (W - 20 - 8) / 3, byy = py + H - 22;
        for (int i = 0; i < 3; i++) {
            int x = px + 10 + i * (bwid + 4);
            boolean hov = in(mx, my, x, byy, bwid, 16);
            c.fill(x, byy, x + bwid, byy + 16, (hov ? 0x66000000 : 0x40000000) | accent);
            c.drawText(tr, labels[i], x + (bwid - tr.getWidth(labels[i])) / 2, byy + 4, txt, false);
            if (hov && down && !wasDown) {
                if (i == 0) Spotify.previous();
                else if (i == 1) Spotify.toggle();
                else Spotify.next();
            }
        }
        wasDown = down;
    }

    private static void centered(DrawContext c, TextRenderer tr, String s, int px, int y, int color) {
        c.drawText(tr, s, px + (W - tr.getWidth(s)) / 2, y, color, false);
    }
}
