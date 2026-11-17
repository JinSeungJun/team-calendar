package io.github.jinseungjun.teamcal;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/** 앱 화면: 웹 캘린더를 그대로 띄우고, 팀 코드·일정을 홈 화면 위젯과 공유 */
public class MainActivity extends Activity {

    static final String HOST = "jinseungjun.github.io";
    static final String URL = "https://" + HOST + "/team-calendar/?app=1";

    private WebView web;
    private boolean pageReady = false;
    private boolean pendingAdd = false;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " TeamCalApp/1");

        web.addJavascriptInterface(new Bridge(getApplicationContext()), "TeamCalApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // 그 외 링크는 기본 브라우저로
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageReady = true;
                if (pendingAdd) { pendingAdd = false; openAdd(); }
            }
        });

        pendingAdd = getIntent().getBooleanExtra("add", false);
        if (saved != null) web.restoreState(saved);
        else web.loadUrl(URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("add", false)) {
            if (pageReady) openAdd(); else pendingAdd = true;
        }
    }

    private void openAdd() {
        // 페이지가 Firebase 연결을 끝낼 시간을 조금 줌
        ui.postDelayed(() -> web.evaluateJavascript("window.teamcalAdd && window.teamcalAdd()", null), 400);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CalendarWidget.updateAll(getApplicationContext());
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** 웹 페이지(index.html)에서 호출하는 함수들 */
    static final class Bridge {
        private final Context c;
        private final Handler h = new Handler(Looper.getMainLooper());
        private final Runnable update;

        Bridge(Context c) {
            this.c = c;
            this.update = () -> CalendarWidget.updateAll(c);
        }

        @JavascriptInterface
        public void setTeam(String team) {
            String old = Store.team(c);
            Store.setTeam(c, team);
            if (!old.equals(team)) h.post(update);
        }

        /** 앱이 열려 있는 동안 일정이 바뀌면 위젯도 바로 갱신 */
        @JavascriptInterface
        public void setData(String json) {
            Store.setCache(c, json);
            h.removeCallbacks(update);
            h.postDelayed(update, 500);
        }

        @JavascriptInterface
        public void copy(String text) {
            h.post(() -> {
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("팀 캘린더", text));
                Toast.makeText(c, "복사했어요", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
