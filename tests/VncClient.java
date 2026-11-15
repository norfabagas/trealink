package dev.trealink.app;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Independent test client: its DES key construction uses a lookup, not server code. */
public final class VncClient implements AutoCloseable {
    final Socket socket;
    final DataInputStream in;
    final DataOutputStream out;
    public final int width, height;
    public VncClient(String host, int port, String password) throws Exception {
        socket = new Socket(host, port); socket.setSoTimeout(10000);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try {
            byte[] version = new byte[12]; in.readFully(version);
            if (!new String(version, StandardCharsets.US_ASCII).equals("RFB 003.008\n")) throw new IOException("Bad RFB banner");
            out.write(version); out.flush();
            if (in.readUnsignedByte() != 1 || in.readUnsignedByte() != 2) throw new IOException("Server must require VNC auth");
            out.writeByte(2); out.flush();
            byte[] challenge = new byte[16]; in.readFully(challenge);
            byte[] reversed = new byte[8]; byte[] ascii = password.getBytes(StandardCharsets.US_ASCII);
            String[] nibble = {"0", "8", "4", "c", "2", "a", "6", "e", "1", "9", "5", "d", "3", "b", "7", "f"};
            for (int i = 0; i < 8; i++) { int c = i < ascii.length ? ascii[i] & 255 : 0; reversed[i] = (byte)Integer.parseInt(nibble[c & 15] + nibble[c >>> 4], 16); }
            Cipher des = Cipher.getInstance("DES/ECB/NoPadding"); des.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(reversed, "DES"));
            out.write(des.doFinal(challenge)); out.flush();
            if (in.readInt() != 0) throw new IOException("Authentication rejected");
            out.writeByte(1); out.flush();
            width = in.readUnsignedShort(); height = in.readUnsignedShort();
            byte[] format = new byte[16]; in.readFully(format);
            int length = in.readInt(); if (length < 0 || length > 1024) throw new IOException("Bad desktop name");
            byte[] name = new byte[length]; in.readFully(name);
        } catch (Exception e) { socket.close(); throw e; }
    }
    void format565() throws IOException {
        out.write(new byte[]{0, 0, 0, 0, 16, 16, 1, 1});
        out.writeShort(31); out.writeShort(63); out.writeShort(31);
        out.write(new byte[]{11, 5, 0, 0, 0, 0}); out.flush();
    }
    byte[] request(int x, int y, int w, int h, int bytesPerPixel) throws IOException {
        out.writeByte(3); out.writeByte(0); out.writeShort(x); out.writeShort(y); out.writeShort(w); out.writeShort(h); out.flush();
        if (in.readUnsignedByte() != 0 || in.readUnsignedByte() != 0) throw new IOException("Bad update header");
        int rects = in.readUnsignedShort(); if (rects == 0) return new byte[0];
        if (rects != 1 || in.readUnsignedShort() != x || in.readUnsignedShort() != y) throw new IOException("Bad update rectangle");
        int rw = in.readUnsignedShort(), rh = in.readUnsignedShort();
        if (in.readInt() != 0) throw new IOException("Expected raw encoding");
        byte[] pixels = new byte[rw * rh * bytesPerPixel]; in.readFully(pixels); return pixels;
    }
    @Override public void close() throws IOException { socket.close(); }
}
