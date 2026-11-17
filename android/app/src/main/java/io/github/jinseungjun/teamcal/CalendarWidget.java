package io.github.jinseungjun.teamcal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 홈 화면 위젯: 다가오는 팀 일정 목록 */
public class CalendarWidget extends AppWidgetProvider {

    static final String ACTION_REFRESH = "io.github.jinseungjun.teamcal.REFRESH";

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        render(c, mgr, ids, false);
        fetchAsync(c, goAsync());
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            render(c, mgr, ids(c), true);
            fetchAsync(c, goAsync());
        }
    }

    private static void fetchAsync(Context ctx, PendingResult pr) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                Api.refresh(c);
                updateAll(c);
            } finally {
                if (pr != null) pr.finish();
            }
        }).start();
    }

    /** 앱에서 데이터가 바뀌었을 때 호출 (네트워크 없이 캐시로 바로 갱신) */
    static void updateAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = ids(c);
        if (ids.length == 0) return;
        render(c, mgr, ids, false);
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.list);
    }

    /** 앱에서 네트워크로 새로 받아오게 할 때 */
    static void refreshAsync(Context c) {
        if (ids(c).length == 0) return;
        fetchAsync(c, null);
    }

    private static int[] ids(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, CalendarWidget.class));
    }

    private static void render(Context c, AppWidgetManager mgr, int[] ids, boolean loading) {
        boolean hasTeam = !Store.team(c).isEmpty();
        String today = new SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(new Date());
        long at = Store.cacheAt(c);
        String updated = loading ? "갱신 중…" : (at > 0 ? new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(at)) + " 기준" : "");

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.title, "📅 " + today);
            rv.setTextViewText(R.id.updated, updated);
            rv.setTextViewText(R.id.empty, hasTeam ? "다가오는 일정이 없어요" : "앱을 열어 팀에 입장해 주세요");

            // 목록 (위젯마다 다른 데이터 URI를 줘야 서로 섞이지 않음)
            Intent svc = new Intent(c, WidgetService.class);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
            rv.setRemoteAdapter(R.id.list, svc);
            rv.setEmptyView(R.id.list, R.id.empty);

            // 제목 누르면 앱 열기
            rv.setOnClickPendingIntent(R.id.title, openApp(c, 1, false));
            rv.setOnClickPendingIntent(R.id.empty, openApp(c, 2, false));
            // ＋ 누르면 앱 열고 일정 추가 창
            rv.setOnClickPendingIntent(R.id.add, openApp(c, 3, true));
            // ↻ 새로고침
            Intent r = new Intent(c, CalendarWidget.class).setAction(ACTION_REFRESH);
            rv.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 4, r,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            // 목록 항목 누르면 앱 열기 (fill-in 인텐트를 쓰므로 MUTABLE)
            Intent tpl = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            rv.setPendingIntentTemplate(R.id.list, PendingIntent.getActivity(c, 5, tpl,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

            mgr.updateAppWidget(id, rv);
        }
    }

    private static PendingIntent openApp(Context c, int req, boolean add) {
        Intent i = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("add", add);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
