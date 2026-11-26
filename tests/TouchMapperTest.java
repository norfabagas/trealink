package dev.trealink.app;

public final class TouchMapperTest {
    private static int checks;
    static void point(float[] actual, float x, float y, String label) {
        if (actual == null || Math.abs(actual[0]-x) > .01 || Math.abs(actual[1]-y) > .01) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    static void absent(float[] actual, String label) { if (actual != null) throw new AssertionError(label); checks++; System.out.println("PASS " + label); }
    public static void main(String[] args) {
        point(TouchMapper.map(160,320,320,640,720,1440),360,720,"scaled portrait coordinate");
        point(TouchMapper.map(160,320,320,640,640,320),320,160,"landscape source in portrait letterbox");
        point(TouchMapper.map(0,240,320,640,640,320),0,0,"letterbox content origin");
        absent(TouchMapper.map(160,239,320,640,640,320),"top letterbox rejected");
        absent(TouchMapper.map(160,400,320,640,640,320),"bottom letterbox rejected");
        point(TouchMapper.map(320,160,640,320,320,640),160,320,"portrait source in landscape pillarbox");
        absent(TouchMapper.map(239,160,640,320,320,640),"side pillarbox rejected");
        absent(TouchMapper.map(-1,0,320,640,320,640),"negative coordinate rejected");
        absent(TouchMapper.map(320,0,320,640,320,640),"outside frame rejected");
        absent(TouchMapper.map(0,0,0,640,320,640),"invalid dimensions rejected");
        System.out.println("RESULT " + checks + " mapping checks passed");
    }
}
