package io.github.jinseungjun.teamcal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 월간 달력을 그림(Bitmap)으로 그림 — 홈 화면 위젯에 표처럼 넣기 위해 */
final class MonthRenderer {
    private MonthRenderer() {}

    static final int ACCENT = Color.parseColor("#2F6FED");
    static final int TEXT = Color.parseColor("#1F2328");
    static final int SUB = Color.parseColor("#9AA1AB");
    static final int SUN = Color.parseColor("#D11A2A");
    static final int SAT = Color.parseColor("#2563EB");
    static final int LINE = Color.parseColor("#1F000000");
    static final int OTHER_BG = Color.parseColor("#0A000000");
    static final int GRAY = Color.parseColor("#6B7280");

    /** 달력 첫 칸(일요일) 날짜 yyyy-MM-dd */
    static String gridStart(int year, int month0) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month0, 1);
        c.add(Calendar.DAY_OF_MONTH, -(c.get(Calendar.DAY_OF_WEEK) - 1));
        return ymd(c);
    }

    static String ymd(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * @param w,h  그릴 크기(px)
     * @param d    dp → px 배율
     */
    static Bitmap draw(int w, int h, float d, int year, int month0, String cacheJson, boolean hasTeam) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(w, 10), Math.max(h, 10), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        // 데이터
        List<JSONObject> events = new ArrayList<>();
        Map<String, Integer> pc = new HashMap<>(), tc = new HashMap<>();
        if (cacheJson != null && !cacheJson.isEmpty()) {
            try {
                JSONObject o = new JSONObject(cacheJson);
                colors(o.optJSONArray("people"), pc);
                colors(o.optJSONArray("types"), tc);
                JSONArray arr = o.optJSONArray("events");
                if (arr != null) for (int i = 0; i < arr.length(); i++) events.add(arr.getJSONObject(i));
            } catch (Exception ignored) {}
        }
        events.sort((a, b) -> {
            int r = a.optString("start").compareTo(b.optString("start"));
            if (r != 0) return r;
            r = tk(a).compareTo(tk(b));
            return r != 0 ? r : a.optString("title").compareTo(b.optString("title"));
        });

        // 칸 크기
        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(year, month0, 1);
        int firstDow = first.get(Calendar.DAY_OF_WEEK) - 1;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int weeks = (firstDow + days + 6) / 7;
        float dowH = 16 * d;
        float cw = w / 7f, ch = (h - dowH) / weeks;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        // 요일 머리줄
        String[] dows = {"일", "월", "화", "수", "목", "금", "토"};
        tp.setTextSize(10.5f * d);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        for (int i = 0; i < 7; i++) {
            tp.setColor(i == 0 ? SUN : i == 6 ? SAT : GRAY);
            cv.drawText(dows[i], cw * i + cw / 2, dowH - 4.5f * d, tp);
        }

        String today = ymd(Calendar.getInstance());
        Calendar cur = Calendar.getInstance();
        cur.clear();
        cur.set(year, month0, 1);
        cur.add(Calendar.DAY_OF_MONTH, -firstDow);

        float numH = 15 * d, chipH = 12.5f * d, gap = 1.5f * d, padX = 1.5f * d;
        int maxChips = Math.max(0, (int) ((ch - numH - 1 * d) / (chipH + gap)));

        for (int i = 0; i < weeks * 7; i++) {
            int col = i % 7, row = i / 7;
            float x = cw * col, y = dowH + ch * row;
            String key = ymd(cur);
            boolean other = cur.get(Calendar.MONTH) != month0;

            if (other) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(OTHER_BG);
                cv.drawRect(x, y, x + cw, y + ch, p);
            }

            // 날짜 숫자
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextSize(11 * d);
            String num = String.valueOf(cur.get(Calendar.DAY_OF_MONTH));
            float nx = x + 4 * d, ny = y + 11.5f * d;
            if (key.equals(today)) {
                float tw = tp.measureText(num);
                p.setColor(ACCENT);
                cv.drawRoundRect(new RectF(nx - 3 * d, y + 1.5f * d, nx + tw + 3 * d, y + 14 * d), 7 * d, 7 * d, p);
                tp.setColor(Color.WHITE);
            } else {
                tp.setColor(other ? SUB : col == 0 ? SUN : col == 6 ? SAT : TEXT);
            }
            cv.drawText(num, nx, ny, tp);

            // 그날 일정
            List<JSONObject> list = new ArrayList<>();
            for (JSONObject e : events) {
                String s = e.optString("start"), en = e.optString("end", s);
                if (en.isEmpty()) en = s;
                if (s.compareTo(key) <= 0 && key.compareTo(en) <= 0) list.add(e);
            }
            int show = list.size() > maxChips ? Math.max(0, maxChips - 1) : list.size();
            float cy = y + numH;
            tp.setTypeface(Typeface.DEFAULT);
            tp.setTextSize(9 * d);
            for (int k = 0; k < show; k++) {
                JSONObject e = list.get(k);
                int color = get(pc, e.optString("person"), GRAY);
                int tcol = get(tc, e.optString("type"), Color.TRANSPARENT);
                RectF r = new RectF(x + padX, cy, x + cw - padX, cy + chipH);
                p.setColor(color);
                cv.drawRoundRect(r, 3 * d, 3 * d, p);
                float tx = r.left + 3 * d;
                if (tcol != Color.TRANSPARENT && !e.optString("type").isEmpty()) {
                    // 왼쪽 띠 = 구분 색
                    p.setColor(tcol);
                    cv.drawRoundRect(new RectF(r.left, r.top, r.left + 3 * d, r.bottom), 1.5f * d, 1.5f * d, p);
                    tx = r.left + 5 * d;
                }
                tp.setColor(Color.WHITE);
                CharSequence t = TextUtils.ellipsize(e.optString("title"), tp, r.right - tx - 2 * d, TextUtils.TruncateAt.END);
                cv.drawText(t, 0, t.length(), tx, r.bottom - 3.2f * d, tp);
                cy += chipH + gap;
            }
            if (list.size() > show) {
                tp.setColor(GRAY);
                tp.setTypeface(Typeface.DEFAULT_BOLD);
                cv.drawText("+" + (list.size() - show), x + 4 * d, cy + 9 * d, tp);
                tp.setTypeface(Typeface.DEFAULT);
            }
            cur.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 칸 선
        p.setColor(LINE);
        p.setStrokeWidth(Math.max(1, 0.7f * d));
        for (int r = 0; r <= weeks; r++) cv.drawLine(0, dowH + ch * r, w, dowH + ch * r, p);
        for (int c = 1; c < 7; c++) cv.drawLine(cw * c, dowH, cw * c, h, p);

        if (!hasTeam) {
            p.setColor(Color.parseColor("#E6FFFFFF"));
            cv.drawRect(0, 0, w, h, p);
            tp.setColor(TEXT);
            tp.setTextSize(13 * d);
            tp.setTextAlign(Paint.Align.CENTER);
            cv.drawText("앱을 열어 팀에 입장해 주세요", w / 2f, h / 2f, tp);
        }
        return bmp;
    }

    private static String tk(JSONObject e) { String t = e.optString("time"); return t.isEmpty() ? "99" : t; }

    private static void colors(JSONArray arr, Map<String, Integer> m) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try { m.put(o.optString("name"), Color.parseColor(o.optString("color"))); } catch (Exception ignored) {}
        }
    }

    private static int get(Map<String, Integer> m, String k, int def) { Integer v = m.get(k); return v == null ? def : v; }
}
