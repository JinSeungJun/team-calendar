package io.github.jinseungjun.teamcal;

import android.content.Context;
import android.content.SharedPreferences;

/** 앱/위젯 공용 저장소 (팀 코드, 일정 캐시, 로그인 토큰) */
final class Store {
    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("teamcal", Context.MODE_PRIVATE);
    }

    static String team(Context c) { return p(c).getString("team", ""); }

    static void setTeam(Context c, String team) {
        String old = team(c);
        SharedPreferences.Editor e = p(c).edit().putString("team", team == null ? "" : team);
        if (!old.equals(team)) e.remove("cache"); // 팀이 바뀌면 캐시 비우기
        e.apply();
    }

    static String cache(Context c) { return p(c).getString("cache", ""); }
    static void setCache(Context c, String json) { p(c).edit().putString("cache", json).putLong("cacheAt", System.currentTimeMillis()).apply(); }
    static long cacheAt(Context c) { return p(c).getLong("cacheAt", 0); }

    static String get(Context c, String k) { return p(c).getString(k, ""); }
    static long getLong(Context c, String k) { return p(c).getLong(k, 0); }
    static void put(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
    static void putLong(Context c, String k, long v) { p(c).edit().putLong(k, v).apply(); }
}
