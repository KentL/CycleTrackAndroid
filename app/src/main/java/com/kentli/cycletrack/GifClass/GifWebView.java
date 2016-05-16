package com.kentli.cycletrack.GifClass;

import android.content.Context;
import android.webkit.WebView;

/**
 * Created by zhuol on 5/13/2016.
 */
public class GifWebView extends WebView {

    public GifWebView(Context context, String path) {
        super(context);
        loadUrl(path);
    }
}