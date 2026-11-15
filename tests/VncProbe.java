package dev.trealink.app;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Connects to a running Android session, authenticates, and saves actual captured pixels. */
public final class VncProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Usage: VncProbe HOST PORT PASSWORD OUTPUT.png");
        try (VncClient client = new VncClient(args[0], Integer.parseInt(args[1]), args[2])) {
            byte[] pixels = client.request(0, 0, client.width, client.height, 4);
            BufferedImage image = new BufferedImage(client.width, client.height, BufferedImage.TYPE_INT_RGB);
            int nonBlack = 0;
            for (int y = 0; y < client.height; y++) for (int x = 0; x < client.width; x++) {
                int i = (y * client.width + x) * 4;
                int rgb = (pixels[i] & 255) | ((pixels[i + 1] & 255) << 8) | ((pixels[i + 2] & 255) << 16);
                image.setRGB(x, y, rgb); if (rgb != 0) nonBlack++;
            }
            if (nonBlack < client.width * client.height / 10) throw new AssertionError("Frame is almost entirely black");
            ImageIO.write(image, "png", new File(args[3]));
            System.out.println("PASS Android VNC frame " + client.width + "x" + client.height + ", " + nonBlack + " nonblack pixels, saved to " + args[3]);
        }
    }
}
