package io.github.jinseungjun.teamcal;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.Calendar;

/** 홈 화면 위젯: 월간 달력 (◀ ▶로 달 이동) */
public class CalendarWidget extends AppWidgetProvider {

    static final String ACTION_REFRESH = "io.github.jinseungjun.teamcal.REFRESH";
    static final String ACTION_PREV = "io.github.jinseungjun.teamcal.PREV";
    static final String ACTION_NEXT = "io.github.jinseungjun.teamcal.NEXT";
    static final String ACTION_TODAY = "io.github.jinseungjun.teamcal.TODAY";

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        render(c, mgr, ids, false);
        fetchAsync(c, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager mgr, int id, Bundle newOptions) {
        render(c, mgr, new int[]{id}, false); // 크기 바뀌면 다시 그림
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        String a = intent.getAction();
        if (a == null) return;
        int off = (int) Store.getLong(c, "monthOffset");
        switch (a) {
            case ACTION_PREV: Store.putLong(c, "monthOffset", off - 1); break;
            case ACTION_NEXT: Store.putLong(c, "monthOffset", off + 1); break;
            case ACTION_TODAY: Store.putLong(c, "monthOffset", 0); break;
            case ACTION_REFRESH: break;
            default: return;
        }
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        boolean loading = ACTION_REFRESH.equals(a);
        render(c, mgr, ids(c), loading);
        // 지난달로 가거나 새로고침이면 서버에서 다시 받기
        if (loading || ACTION_PREV.equals(a)) fetchAsync(c, goAsync());
    }

    private static void fetchAsync(Context ctx, PendingResult pr) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                int[] ym = viewMonth(c);
                Api.refresh(c, MonthRenderer.gridStart(ym[0], ym[1]));
                updateAll(c);
            } finally {
                if (pr != null) pr.finish();
            }
        }).start();
    }

    /** 앱에서 데이터가 바뀌었을 때 (캐시로 바로 다시 그림) */
    static void updateAll(Context c) {
        int[] ids = ids(c);
        if (ids.length == 0) return;
        render(c, AppWidgetManager.getInstance(c), ids, false);
    }

    static void refreshAsync(Context c) {
        if (ids(c).length == 0) return;
        fetchAsync(c, null);
    }

    private static int[] ids(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, CalendarWidget.class));
    }

    /** 위젯이 보여주는 연·월(0부터) */
    static int[] viewMonth(Context c) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.MONTH, (int) Store.getLong(c, "monthOffset"));
        return new int[]{cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)};
    }

    private static void render(Context c, AppWidgetManager mgr, int[] ids, boolean loading) {
        boolean hasTeam = !Store.team(c).isEmpty();
        int[] ym = viewMonth(c);
        String cache = Store.cache(c);
        float density = c.getResources().getDisplayMetrics().density;

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.title, ym[0] + "년 " + (ym[1] + 1) + "월");
            rv.setTextViewText(R.id.refresh, loading ? "…" : "↻");

            // 위젯 실제 크기(dp) → 달력 그림 크기(px)
            Bundle o = mgr.getAppWidgetOptions(id);
            int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (wDp <= 0) wDp = 320;
            if (hDp <= 0) hDp = 360;
            float calWdp = wDp - 16, calHdp = hDp - 12 - 36; // 위젯 여백·머리줄 빼기
            float d = density;
            // 그림이 너무 크면 위젯 갱신이 거부되므로 해상도 제한 (약 140만 픽셀)
            double px = (calWdp * d) * (calHdp * d);
            if (px > 1_400_000) d = (float) (d * Math.sqrt(1_400_000 / px));
            Bitmap bmp = MonthRenderer.draw((int) (calWdp * d), (int) (calHdp * d), d, ym[0], ym[1], cache, hasTeam);
            rv.setImageViewBitmap(R.id.cal, bmp);

            rv.setOnClickPendingIntent(R.id.cal, openApp(c, 1, false));
            rv.setOnClickPendingIntent(R.id.title, openApp(c, 2, false));
            rv.setOnClickPendingIntent(R.id.add, openApp(c, 3, true));
            rv.setOnClickPendingIntent(R.id.refresh, action(c, ACTION_REFRESH, 4));
            rv.setOnClickPendingIntent(R.id.prev, action(c, ACTION_PREV, 5));
            rv.setOnClickPendingIntent(R.id.next, action(c, ACTION_NEXT, 6));
            rv.setOnClickPendingIntent(R.id.today, action(c, ACTION_TODAY, 7));
            mgr.updateAppWidget(id, rv);
        }
    }

    private static PendingIntent action(Context c, String act, int req) {
        Intent i = new Intent(c, CalendarWidget.class).setAction(act);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openApp(Context c, int req, boolean add) {
        Intent i = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("add", add);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
