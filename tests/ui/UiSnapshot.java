package dev.trealink.uiprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.StringWriter;
import org.xmlpull.v1.XmlSerializer;

/** Test-only snapshotter that preserves the service being tested. Separate package/process. */
public final class UiSnapshot extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityNodeInfo root = null;
            for (int i=0;i<20 && root==null;i++) { root=automation.getRootInActiveWindow(); if(root==null) Thread.sleep(100); }
            if (root==null) throw new IllegalStateException("No active UI root");
            StringWriter out = new StringWriter(); XmlSerializer xml = android.util.Xml.newSerializer();
            xml.setOutput(out); xml.startDocument("UTF-8", true); xml.startTag("", "hierarchy");
            write(root,xml,0); root.recycle();
            xml.endTag("", "hierarchy"); xml.endDocument(); result.putString("xml",out.toString());
            finish(Activity.RESULT_OK,result);
        } catch (Exception e) { result.putString("error",e.toString()); finish(Activity.RESULT_CANCELED,result); }
    }
    private void write(AccessibilityNodeInfo node, XmlSerializer xml, int depth) throws Exception {
        if(depth>50) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        xml.startTag("","node");
        xml.attribute("","text",node.getText()==null ? "" : node.getText().toString());
        xml.attribute("","class",node.getClassName()==null ? "" : node.getClassName().toString());
        xml.attribute("","content-desc",node.getContentDescription()==null ? "" : node.getContentDescription().toString());
        xml.attribute("","enabled",Boolean.toString(node.isEnabled()));
        xml.attribute("","bounds","["+bounds.left+","+bounds.top+"]["+bounds.right+","+bounds.bottom+"]");
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null) { if(child.isVisibleToUser()) write(child,xml,depth+1); child.recycle(); }
        }
        xml.endTag("","node");
    }
}
