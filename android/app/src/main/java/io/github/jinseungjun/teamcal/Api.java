package io.github.jinseungjun.teamcal;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 위젯용 데이터 가져오기 — Firebase SDK 없이 REST로 직접 호출
 * (앱이 꺼져 있어도 위젯이 30분마다/새로고침 버튼으로 최신 일정을 가져옴)
 */
final class Api {
    private Api() {}

    // config.js 와 같은 값 (공개돼도 되는 값)
    static final String API_KEY = "AIzaSyBiH2sqkNQlQVvTkspiicLz4YZQRvUyquE";
    static final String PROJECT = "onearch-team-calendar";
    static final String BASE = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/";

    /** 팀 일정과 설정을 받아 캐시에 저장. 성공하면 true */
    static boolean refresh(Context c) {
        String team = Store.team(c);
        if (team.isEmpty()) return false;
        try {
            String token = idToken(c);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());

            // 설정 (담당자·구분 색상)
            JSONObject out = new JSONObject();
            JSONArray people = new JSONArray(), types = new JSONArray();
            try {
                JSONObject s = new JSONObject(http("GET", BASE + "teams/" + team + "/meta/settings", null, token, "application/json"));
                JSONObject f = s.optJSONObject("fields");
                if (f != null) {
                    people = nameColorList(f.optJSONObject("people"));
                    types = nameColorList(f.optJSONObject("types"));
                }
            } catch (HttpError e) {
                if (e.code != 404) throw e; // 설정이 아직 없으면 무시
            }
            out.put("people", people);
            out.put("types", types);

            // 오늘 이후에 끝나는 일정
            String q = "{\"structuredQuery\":{\"from\":[{\"collectionId\":\"events\"}],"
                    + "\"where\":{\"fieldFilter\":{\"field\":{\"fieldPath\":\"end\"},\"op\":\"GREATER_THAN_OR_EQUAL\","
                    + "\"value\":{\"stringValue\":\"" + today + "\"}}},\"limit\":300}}";
            JSONArray res = new JSONArray(http("POST", BASE + "teams/" + team + ":runQuery", q, token, "application/json"));
            JSONArray events = new JSONArray();
            for (int i = 0; i < res.length(); i++) {
                JSONObject d = res.getJSONObject(i).optJSONObject("document");
                if (d == null) continue;
                JSONObject f = d.optJSONObject("fields");
                if (f == null) continue;
                String name = d.optString("name");
                JSONObject ev = new JSONObject();
                ev.put("id", name.substring(name.lastIndexOf('/') + 1));
                for (String k : new String[]{"start", "end", "time", "title", "person", "type"}) ev.put(k, str(f, k));
                events.put(ev);
            }
            out.put("events", events);
            Store.setCache(c, out.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JSONArray nameColorList(JSONObject arrField) {
        JSONArray out = new JSONArray();
        if (arrField == null) return out;
        JSONObject av = arrField.optJSONObject("arrayValue");
        JSONArray vals = av == null ? null : av.optJSONArray("values");
        if (vals == null) return out;
        for (int i = 0; i < vals.length(); i++) {
            JSONObject mv = vals.optJSONObject(i);
            JSONObject f = mv == null ? null : mv.optJSONObject("mapValue");
            f = f == null ? null : f.optJSONObject("fields");
            if (f == null) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("name", str(f, "name"));
                o.put("color", str(f, "color"));
                out.put(o);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static String str(JSONObject fields, String k) {
        JSONObject v = fields.optJSONObject(k);
        return v == null ? "" : v.optString("stringValue", "");
    }

    /** 익명 로그인 토큰 (만료되면 갱신, 없으면 새로 발급) */
    private static String idToken(Context c) throws Exception {
        String tok = Store.get(c, "idToken");
        long exp = Store.getLong(c, "idTokenExp");
        if (!tok.isEmpty() && exp > System.currentTimeMillis() + 60_000) return tok;

        String refresh = Store.get(c, "refreshToken");
        if (!refresh.isEmpty()) {
            try {
                String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8");
                JSONObject r = new JSONObject(http("POST", "https://securetoken.googleapis.com/v1/token?key=" + API_KEY,
                        body, null, "application/x-www-form-urlencoded"));
                save(c, r.getString("id_token"), r.optString("refresh_token", refresh), r.optLong("expires_in", 3600));
                return Store.get(c, "idToken");
            } catch (Exception ignored) {
                // 갱신 실패 → 새로 발급
            }
        }
        JSONObject r = new JSONObject(http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + API_KEY,
                "{\"returnSecureToken\":true}", null, "application/json"));
        save(c, r.getString("idToken"), r.getString("refreshToken"), Long.parseLong(r.optString("expiresIn", "3600")));
        return Store.get(c, "idToken");
    }

    private static void save(Context c, String id, String refresh, long expiresInSec) {
        Store.put(c, "idToken", id);
        Store.put(c, "refreshToken", refresh);
        Store.putLong(c, "idTokenExp", System.currentTimeMillis() + expiresInSec * 1000);
    }

    static final class HttpError extends Exception {
        final int code;
        HttpError(int code, String msg) { super(msg); this.code = code; }
    }

    private static String http(String method, String url, String body, String bearer, String contentType) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        if (bearer != null) con.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
            try (OutputStream os = con.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = con.getResponseCode();
        InputStream is = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String text = "";
        if (is != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            text = bos.toString("UTF-8");
        }
        con.disconnect();
        if (code >= 400) throw new HttpError(code, text);
        return text;
    }
}
