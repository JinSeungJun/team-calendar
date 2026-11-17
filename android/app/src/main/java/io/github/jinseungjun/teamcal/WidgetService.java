package io.github.jinseungjun.teamcal;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 위젯 목록 내용: 날짜 머리줄 + 일정 줄 */
public class WidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static final class Row {
        boolean header;
        String text, title, type, sub;
        int barColor, typeColor;
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context c;
        private final List<Row> rows = new ArrayList<>();

        Factory(Context c) { this.c = c; }

        @Override public void onCreate() {}
        @Override public void onDestroy() { rows.clear(); }
        @Override public int getCount() { return rows.size(); }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return false; }

        @Override
        public void onDataSetChanged() {
            rows.clear();
            if (Store.team(c).isEmpty()) return;
            String json = Store.cache(c);
            if (json.isEmpty()) return;
            try {
                JSONObject o = new JSONObject(json);
                Map<String, Integer> pc = colors(o.optJSONArray("people"));
                Map<String, Integer> tc = colors(o.optJSONArray("types"));
                JSONArray evs = o.optJSONArray("events");
                if (evs == null) return;

                SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
                Calendar cal = Calendar.getInstance();
                String today = ymd.format(cal.getTime());
                cal.add(Calendar.DAY_OF_YEAR, 1);
                String tomorrow = ymd.format(cal.getTime());
                cal.add(Calendar.DAY_OF_YEAR, 44);
                String limit = ymd.format(cal.getTime()); // 45일 앞까지

                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; i < evs.length(); i++) {
                    JSONObject e = evs.getJSONObject(i);
                    String end = e.optString("end", e.optString("start"));
                    if (end.compareTo(today) < 0 || e.optString("start").compareTo(limit) > 0) continue;
                    list.add(e);
                }
                // 이미 시작한 일정은 오늘로 묶어서 정렬
                Collections.sort(list, (a, b) -> {
                    String da = max(a.optString("start"), today), db = max(b.optString("start"), today);
                    int r = da.compareTo(db);
                    if (r != 0) return r;
                    r = tk(a).compareTo(tk(b));
                    return r != 0 ? r : a.optString("title").compareTo(b.optString("title"));
                });

                String lastDay = null;
                for (JSONObject e : list) {
                    String start = e.optString("start"), end = e.optString("end", start);
                    String day = max(start, today);
                    if (!day.equals(lastDay)) {
                        Row h = new Row();
                        h.header = true;
                        h.text = dayLabel(day, today, tomorrow, ymd);
                        rows.add(h);
                        lastDay = day;
                    }
                    Row r = new Row();
                    r.title = e.optString("title");
                    r.type = e.optString("type");
                    r.barColor = get(pc, e.optString("person"), Color.parseColor("#6B7280"));
                    r.typeColor = get(tc, r.type, Color.parseColor("#6B7280"));
                    StringBuilder sb = new StringBuilder();
                    if (!e.optString("person").isEmpty()) sb.append(e.optString("person"));
                    if (!e.optString("time").isEmpty() && start.equals(day)) sb.append(sb.length() > 0 ? " · " : "").append(e.optString("time"));
                    if (!end.equals(start)) sb.append(sb.length() > 0 ? " · " : "").append("~").append(md(end, ymd));
                    r.sub = sb.toString();
                    rows.add(r);
                }
            } catch (Exception ignored) {
                rows.clear();
            }
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= rows.size()) return null;
            Row r = rows.get(position);
            if (r.header) {
                RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_day);
                v.setTextViewText(R.id.day, r.text);
                return v;
            }
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_item);
            v.setTextViewText(R.id.etitle, r.title);
            v.setTextViewText(R.id.etype, r.type == null ? "" : r.type);
            v.setTextColor(R.id.etype, r.typeColor);
            v.setTextViewText(R.id.esub, r.sub);
            v.setInt(R.id.bar, "setColorFilter", r.barColor);
            v.setOnClickFillInIntent(R.id.item, new Intent());
            return v;
        }

        private static String tk(JSONObject e) { String t = e.optString("time"); return t.isEmpty() ? "99" : t; }
        private static String max(String a, String b) { return a.compareTo(b) >= 0 ? a : b; }

        private static Map<String, Integer> colors(JSONArray arr) {
            Map<String, Integer> m = new HashMap<>();
            if (arr == null) return m;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                try { m.put(o.optString("name"), Color.parseColor(o.optString("color"))); } catch (Exception ignored) {}
            }
            return m;
        }

        private static int get(Map<String, Integer> m, String k, int def) { Integer v = m.get(k); return v == null ? def : v; }

        private static String md(String ymdStr, SimpleDateFormat ymd) {
            try { return new SimpleDateFormat("M/d", Locale.KOREA).format(ymd.parse(ymdStr)); } catch (Exception e) { return ymdStr; }
        }

        private static String dayLabel(String day, String today, String tomorrow, SimpleDateFormat ymd) {
            String base;
            try {
                Date d = ymd.parse(day);
                base = new SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(d);
            } catch (Exception e) { base = day; }
            if (day.equals(today)) return "오늘 · " + base;
            if (day.equals(tomorrow)) return "내일 · " + base;
            return base;
        }
    }
}
