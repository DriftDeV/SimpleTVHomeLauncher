package dev.drift.a11ytest;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/**
 * Servizio di accessibilita' di prova. Rende vero AccessibilityManager.isEnabled() e, su richiesta
 * (broadcast dev.drift.a11ytest.DUMP --es tag X), scrive in logcat l'albero cosi' come lo vede un
 * lettore di schermo: senza le view "non importanti" (flagDefault), con finestre e titoli.
 * Righe: A11YDUMP <tag> W|N ... tra A11YDUMP <tag> BEGIN e END.
 */
public class Svc extends AccessibilityService {
    private static final String T = "A11YDUMP";

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String tag = i.getStringExtra("tag") == null ? "x" : i.getStringExtra("tag");
            if (i.getBooleanExtra("watch", false)) watch(tag, 60); else dump(tag);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        registerReceiver(rx, new IntentFilter("dev.drift.a11ytest.DUMP"), Context.RECEIVER_EXPORTED);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(rx); } catch (RuntimeException ignored) { }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
    }

    @Override
    public void onInterrupt() {
    }

    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Ogni 100 ms (max n volte) cerca una regione live con testo e, alla prima trovata, scrive il dump. */
    private void watch(final String tag, final int n) {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        if (r != null && hasLive(r)) {
            dump(tag);
            return;
        }
        if (n > 0) h.postDelayed(() -> watch(tag, n - 1), 100);
        else dump(tag);
    }

    private boolean hasLive(AccessibilityNodeInfo n) {
        if (n.getLiveRegion() != 0 && (n.getText() != null || n.getContentDescription() != null)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null && hasLive(c)) return true;
        }
        return false;
    }

    private void dump(String tag) {
        Log.i(T, tag + " BEGIN");
        List<AccessibilityWindowInfo> ws = getWindows();
        for (AccessibilityWindowInfo w : ws) {
            Rect b = new Rect();
            w.getBoundsInScreen(b);
            Log.i(T, tag + " W type=" + w.getType() + " active=" + w.isActive() + " focused=" + w.isFocused()
                    + " layer=" + w.getLayer() + " title=" + q(w.getTitle()) + " bounds=" + b.flattenToString());
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null) walk(tag, r, 0);
        }
        Log.i(T, tag + " END");
    }

    private void walk(String tag, AccessibilityNodeInfo n, int d) {
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        StringBuilder a = new StringBuilder();
        for (AccessibilityNodeInfo.AccessibilityAction x : n.getActionList()) a.append(x.getId()).append(',');
        float tsz = 0;
        if (n.getText() != null && n.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY, new android.os.Bundle())) {
            AccessibilityNodeInfo.ExtraRenderingInfo x = n.getExtraRenderingInfo();
            if (x != null) tsz = x.getTextSizeInPx();
        }
        AccessibilityNodeInfo.RangeInfo ri = n.getRangeInfo();
        AccessibilityNodeInfo.CollectionItemInfo ci = n.getCollectionItemInfo();
        Log.i(T, tag + " N d=" + d
                + " cls=" + n.getClassName()
                + " text=" + q(n.getText())
                + " desc=" + q(n.getContentDescription())
                + " pane=" + q(n.getPaneTitle())
                + " foc=" + n.isFocusable() + " click=" + n.isClickable() + " sel=" + n.isSelected()
                + " chk=" + n.isChecked() + " head=" + n.isHeading() + " live=" + n.getLiveRegion()
                + " vis=" + n.isVisibleToUser() + " a11yfoc=" + n.isAccessibilityFocused()
                + " range=" + (ri == null ? "-" : ri.getType() + ":" + ri.getMin() + ":" + ri.getMax() + ":" + ri.getCurrent())
                + " coll=" + (ci == null ? "-" : ci.getRowIndex() + ":" + ci.getColumnIndex())
                + " state=" + q(n.getStateDescription())
                + " tsz=" + tsz
                + " acts=" + a
                + " b=" + b.flattenToString());
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) walk(tag, c, d + 1);
        }
    }

    private static String q(CharSequence s) {
        return s == null ? "null" : "'" + s.toString().replace("\n", "\\n") + "'";
    }
}
