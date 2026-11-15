package dev.trealink.app;

/** Sends actual RFB pointer packets; the Android UI test checks the resulting actions. */
public final class RemoteTouchProbe {
    static void pointer(VncClient client, int mask, int x, int y) throws Exception {
        client.out.writeByte(5); client.out.writeByte(mask); client.out.writeShort(x); client.out.writeShort(y); client.out.flush();
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 6) throw new IllegalArgumentException("HOST PORT PASSWORD tap|hover|hold|swipe|down X Y [X2 Y2]");
        try (VncClient client = new VncClient(args[0], Integer.parseInt(args[1]), args[2])) {
            int x = Integer.parseInt(args[4]), y = Integer.parseInt(args[5]);
            String kind = args[3];
            if (kind.equals("hover")) { pointer(client,0,x,y); Thread.sleep(150); return; }
            pointer(client,1,x,y);
            if (kind.equals("swipe")) {
                int x2 = Integer.parseInt(args[6]), y2 = Integer.parseInt(args[7]);
                for (int i=1;i<=16;i++) { Thread.sleep(30); pointer(client,1,x+(x2-x)*i/16,y+(y2-y)*i/16); }
                x=x2; y=y2;
            } else Thread.sleep(kind.equals("hold") ? 850 : 100);
            if (!kind.equals("down")) pointer(client,0,x,y);
            Thread.sleep(600);
            System.out.println("SENT " + kind + " through authenticated RFB");
        }
    }
}
