package dev.trealink.app;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public final class RfbServerTest {
    private static int passed;
    static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); passed++; System.out.println("PASS " + label); }
    public static void main(String[] args) throws Exception {
        AtomicReference<RfbServer.Frame> frames = new AtomicReference<>(new RfbServer.Frame(2, 2, new int[]{0xff0000, 0x00ff00, 0x0000ff, 0xffffff}));
        RfbServer server = new RfbServer("127.0.0.1", 0, "password", frames, value -> {}); server.start();
        int port = server.port();
        try {
            try { new VncClient("127.0.0.1", port, "wrongpwd").close(); throw new AssertionError("Wrong password accepted"); }
            catch (IOException expected) { check(true, "wrong password refused"); }
            try (VncClient client = new VncClient("127.0.0.1", port, "password")) {
                check(client.width == 2 && client.height == 2, "RFB 3.8 authentication and dimensions");
                byte[] pixels = client.request(0, 0, 2, 2, 4);
                check(Arrays.equals(pixels, new byte[]{0,0,(byte)255,0, 0,(byte)255,0,0, (byte)255,0,0,0, (byte)255,(byte)255,(byte)255,0}), "32-bit little-endian RGB pixels");
                check(Arrays.equals(client.request(1, 0, 1, 1, 4), new byte[]{0,(byte)255,0,0}), "requested subrectangle");
                check(client.request(100, 100, 1, 1, 4).length == 0, "out-of-bounds request returns empty update");
                client.format565();
                check(Arrays.equals(client.request(0, 0, 2, 2, 2), new byte[]{(byte)0xf8,0, 7,(byte)0xe0, 0,31, (byte)255,(byte)255}), "RGB565 big-endian negotiation");
                frames.set(new RfbServer.Frame(2, 2, new int[4]));
                check(Arrays.equals(client.request(0, 0, 2, 2, 2), new byte[8]), "updated framebuffer delivered");
                client.out.write(new byte[]{4,1,0,0,0,0,0,65, 5,1,0,0,0,0}); client.out.flush();
                check(client.request(0, 0, 2, 2, 2).length == 8, "view-only key and pointer messages consumed");
                client.out.write(new byte[]{6,0,0,0}); client.out.writeInt(Integer.MAX_VALUE); client.out.flush();
                check(client.in.read() == -1, "oversized clipboard rejected and connection closed");
            }
            try (VncClient client = new VncClient("127.0.0.1", port, "password")) {
                check(client.request(0, 0, 2, 2, 4).length == 16, "viewer can reconnect after malformed peer");
                server.close();
                check(client.in.read() == -1, "stop closes active viewer");
            }
            try { new Socket("127.0.0.1", port).close(); throw new AssertionError("Port stayed open"); }
            catch (IOException expected) { check(true, "stop closes listening port"); }
        } finally { server.close(); }
        System.out.println("RESULT " + passed + " checks passed");
    }
}
