package dev.trealink.app;

/** Maps a fixed framebuffer's letterboxed content to physical display pixels. */
public final class TouchMapper {
    public static float[] map(int x, int y, int frameWidth, int frameHeight, int phoneWidth, int phoneHeight) {
        if (frameWidth < 1 || frameHeight < 1 || phoneWidth < 1 || phoneHeight < 1
                || x < 0 || y < 0 || x >= frameWidth || y >= frameHeight) return null;
        double scale = Math.min((double)frameWidth / phoneWidth, (double)frameHeight / phoneHeight);
        double left = (frameWidth - phoneWidth * scale) / 2, top = (frameHeight - phoneHeight * scale) / 2;
        if (x < left || y < top || x >= left + phoneWidth * scale || y >= top + phoneHeight * scale) return null;
        return new float[]{(float)Math.min(phoneWidth - 1, (x - left) / scale),
                (float)Math.min(phoneHeight - 1, (y - top) / scale)};
    }
}
