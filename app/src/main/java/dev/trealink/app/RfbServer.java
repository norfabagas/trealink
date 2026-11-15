package dev.trealink.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** RFB 3.8 frames and absolute pointers; the caller owns input permission and routing. */
public final class RfbServer implements AutoCloseable {
    public static final class Frame {
        public final int width, height;
        public final int[] rgb;
        public Frame(int width, int height, int[] rgb) {
            if (width < 1 || height < 1 || width > 4096 || height > 4096 || rgb.length != width * height)
                throw new IllegalArgumentException("Invalid framebuffer");
            this.width = width; this.height = height; this.rgb = rgb;
        }
    }
    public interface Listener { void status(String value); }
    public interface PointerListener {
        void pointer(int buttons, int x, int y);
        default void disconnected() { }
    }
    private final AtomicReference<Frame> frames;
    private final String password;
    private final Listener listener;
    private final PointerListener pointerListener;
    private final ServerSocket server;
    private volatile boolean closed;
    private volatile Socket client;
    private Thread worker;

    public RfbServer(String host, int port, String password, AtomicReference<Frame> frames, Listener listener) throws IOException {
        this(host, port, password, frames, listener, null);
    }
    public RfbServer(String host, int port, String password, AtomicReference<Frame> frames, Listener listener, PointerListener pointerListener) throws IOException {
        if (password == null || !password.matches("[!-~]{8}")) throw new IllegalArgumentException("Use exactly 8 ASCII password characters");
        if (frames.get() == null) throw new IllegalArgumentException("Initial frame required");
        this.password = password; this.frames = frames; this.listener = listener;
        this.pointerListener = pointerListener;
        server = new ServerSocket(port, 2, InetAddress.getByName(host));
    }
    public int port() { return server.getLocalPort(); }
    public void start() {
        worker = new Thread(() -> {
            while (!closed) {
                try (Socket socket = server.accept()) {
                    client = socket;
                    if (closed) continue;
                    socket.setSoTimeout(15000);
                    socket.setTcpNoDelay(true);
                    serve(socket);
                } catch (IOException | GeneralSecurityException e) {
                    if (!closed) listener.status("Waiting for viewer · " + e.getClass().getSimpleName());
                } finally { client = null; if (pointerListener != null) pointerListener.disconnected(); }
                if (!closed) listener.status("Waiting for viewer");
            }
        }, "trealink-rfb");
        worker.setDaemon(true); worker.start();
    }
    private void serve(Socket socket) throws IOException, GeneralSecurityException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.writeBytes("RFB 003.008\n"); out.flush();
        byte[] version = new byte[12]; in.readFully(version);
        if (!Arrays.equals(version, "RFB 003.008\n".getBytes(StandardCharsets.US_ASCII))) throw new IOException("RFB 3.8 required");
        out.writeByte(1); out.writeByte(2); out.flush(); // VNC authentication only; never offer unauthenticated access.
        if (in.readUnsignedByte() != 2) throw new IOException("VNC authentication required");
        byte[] challenge = new byte[16]; new SecureRandom().nextBytes(challenge);
        out.write(challenge); out.flush();
        byte[] response = new byte[16]; in.readFully(response);
        if (!MessageDigest.isEqual(response, challengeResponse(password, challenge))) {
            byte[] reason = "Incorrect session password".getBytes(StandardCharsets.UTF_8);
            out.writeInt(1); out.writeInt(reason.length); out.write(reason); out.flush();
            throw new IOException("Authentication failed");
        }
        out.writeInt(0); out.flush(); in.readUnsignedByte(); // ClientInit shared flag
        Frame initial = frames.get();
        out.writeShort(initial.width); out.writeShort(initial.height);
        PixelFormat format = new PixelFormat(32, false, 255, 255, 255, 16, 8, 0);
        out.write(new byte[]{32, 24, 0, 1});
        out.writeShort(255); out.writeShort(255); out.writeShort(255);
        out.write(new byte[]{16, 8, 0, 0, 0, 0});
        byte[] name = "Trealink".getBytes(StandardCharsets.UTF_8);
        out.writeInt(name.length); out.write(name); out.flush();
        listener.status("Viewer connected");
        long lastSent = 0;
        boolean hideCursor = false;
        while (!closed) {
            switch (in.readUnsignedByte()) {
                case 0:
                    in.skipBytes(3);
                    int bpp = in.readUnsignedByte(), depth = in.readUnsignedByte();
                    boolean endian = in.readUnsignedByte() != 0, trueColor = in.readUnsignedByte() != 0;
                    int red = in.readUnsignedShort(), green = in.readUnsignedShort(), blue = in.readUnsignedShort();
                    int rs = in.readUnsignedByte(), gs = in.readUnsignedByte(), bs = in.readUnsignedByte();
                    in.skipBytes(3);
                    if (!trueColor || depth > bpp) throw new IOException("True-color pixel format required");
                    format = new PixelFormat(bpp, endian, red, green, blue, rs, gs, bs);
                    break;
                case 2:
                    in.readUnsignedByte(); int encodings = in.readUnsignedShort();
                    if (encodings > 512) throw new IOException("Too many encodings");
                    for (int i = 0; i < encodings; i++) if (in.readInt() == -239) hideCursor = true;
                    // Raw encoding is always valid in RFB even if not explicitly advertised.
                    break;
                case 3:
                    in.readUnsignedByte();
                    int x = in.readUnsignedShort(), y = in.readUnsignedShort();
                    int requestedWidth = in.readUnsignedShort(), requestedHeight = in.readUnsignedShort();
                    long delay = 200 - (System.currentTimeMillis() - lastSent);
                    if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    Frame frame = frames.get();
                    int w = Math.max(0, Math.min(requestedWidth, frame.width - x));
                    int h = Math.max(0, Math.min(requestedHeight, frame.height - y));
                    out.writeByte(0); out.writeByte(0); out.writeShort((w > 0 && h > 0 ? 1 : 0) + (hideCursor ? 1 : 0));
                    if (hideCursor) {
                        // Negotiated zero-size Cursor shape requests an invisible cursor.
                        out.writeShort(0); out.writeShort(0); out.writeShort(0); out.writeShort(0); out.writeInt(-239);
                        hideCursor = false;
                    }
                    if (w > 0 && h > 0) {
                        out.writeShort(x); out.writeShort(y); out.writeShort(w); out.writeShort(h); out.writeInt(0);
                        byte[] row = new byte[w * (format.bpp / 8)];
                        for (int py = y; py < y + h; py++) {
                            for (int px = 0; px < w; px++) format.encode(frame.rgb[py * frame.width + x + px], row, px * (format.bpp / 8));
                            out.write(row);
                        }
                    }
                    out.flush(); lastSent = System.currentTimeMillis();
                    break;
                case 4: in.readUnsignedByte(); in.readUnsignedShort(); in.readInt(); break; // View only: consume keys.
                case 5:
                    int buttons = in.readUnsignedByte(), px = in.readUnsignedShort(), py = in.readUnsignedShort();
                    Frame target = frames.get();
                    if (pointerListener != null && px < target.width && py < target.height) pointerListener.pointer(buttons, px, py);
                    break;
                case 6:
                    in.skipBytes(3); int length = in.readInt();
                    if (length < 0 || length > 65536) throw new IOException("Clipboard message too large");
                    byte[] discard = new byte[length]; in.readFully(discard); break;
                default: throw new IOException("Unsupported client message");
            }
        }
    }

    public static byte[] challengeResponse(String password, byte[] challenge) throws GeneralSecurityException {
        byte[] key = Arrays.copyOf(password.getBytes(StandardCharsets.US_ASCII), 8);
        for (int i = 0; i < key.length; i++) key[i] = (byte)(Integer.reverse(key[i] & 255) >>> 24);
        Cipher des = Cipher.getInstance("DES/ECB/NoPadding");
        des.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DES"));
        return des.doFinal(challenge);
    }

    private static final class PixelFormat {
        final int bpp, red, green, blue, rs, gs, bs; final boolean big;
        PixelFormat(int bpp, boolean big, int red, int green, int blue, int rs, int gs, int bs) throws IOException {
            if ((bpp != 8 && bpp != 16 && bpp != 32) || !validChannel(red, rs, bpp)
                    || !validChannel(green, gs, bpp) || !validChannel(blue, bs, bpp)
                    || (((long)red << rs) & ((long)green << gs)) != 0
                    || (((long)red << rs) & ((long)blue << bs)) != 0
                    || (((long)green << gs) & ((long)blue << bs)) != 0) throw new IOException("Unsupported pixel format");
            this.bpp = bpp; this.big = big; this.red = red; this.green = green; this.blue = blue;
            this.rs = rs; this.gs = gs; this.bs = bs;
        }
        static boolean validChannel(int max, int shift, int bpp) {
            return max > 0 && (max & (max + 1)) == 0 && shift < bpp && 32 - Integer.numberOfLeadingZeros(max) + shift <= bpp;
        }
        void encode(int rgb, byte[] output, int offset) {
            long value = ((long)(((rgb >>> 16) & 255) * red / 255) << rs)
                    | ((long)(((rgb >>> 8) & 255) * green / 255) << gs) | ((long)((rgb & 255) * blue / 255) << bs);
            int size = bpp / 8;
            for (int i = 0; i < size; i++) output[offset + i] = (byte)(value >>> (8 * (big ? size - 1 - i : i)));
        }
    }
    @Override public void close() throws IOException {
        closed = true;
        try { server.close(); } finally { Socket active = client; if (active != null) active.close(); }
        if (worker != null) worker.interrupt();
    }
}
