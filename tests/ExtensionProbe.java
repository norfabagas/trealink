package dev.trealink.app;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Checks pixels from the actual app-owned virtual display, not a mocked frame. */
public final class ExtensionProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("HOST PORT PASSWORD OUTPUT.png tap|view");
        try (VncClient client = new VncClient(args[0], Integer.parseInt(args[1]), args[2])) {
            if (client.width != 800 || client.height != 480) throw new AssertionError("Expected independent 800x480 display");
            byte[] pixels = client.request(0, 0, 800, 480, 4);
            if (args[4].equals("tap")) {
                client.out.writeByte(5); client.out.writeByte(1); client.out.writeShort(400); client.out.writeShort(280);
                client.out.writeByte(5); client.out.writeByte(0); client.out.writeShort(400); client.out.writeShort(280); client.out.flush();
                byte[] before = pixels;
                long deadline = System.currentTimeMillis() + 5000;
                do {
                    Thread.sleep(250);
                    pixels = client.request(0, 0, 800, 480, 4);
                } while (sameButton(before, pixels) && System.currentTimeMillis() < deadline);
                if (sameButton(before, pixels)) throw new AssertionError("Tap did not update the actual streamed test button");
            }
            BufferedImage image = new BufferedImage(800, 480, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 480; y++) for (int x = 0; x < 800; x++) {
                int i = (y * 800 + x) * 4;
                image.setRGB(x, y, (pixels[i] & 255) | ((pixels[i + 1] & 255) << 8) | ((pixels[i + 2] & 255) << 16));
            }
            if ((image.getRGB(10,10) & 0xffffff) != 0x121d26 || (image.getRGB(50,250) & 0xffffff) != 0x6ae0c4)
                throw new AssertionError("Independent surface markers missing: phone pixels or blank display received");
            ImageIO.write(image, "png", new File(args[3]));
            System.out.println("PASS independent 800x480 display markers; " + args[4] + "; " + args[3]);
        }
    }
    private static boolean sameButton(byte[] before, byte[] after) {
        for (int y = 240; y < 330; y++) for (int x = 40 * 4; x < 760 * 4; x++) {
            int i = y * 800 * 4 + x;
            if (before[i] != after[i]) return false;
        }
        return true;
    }
}
