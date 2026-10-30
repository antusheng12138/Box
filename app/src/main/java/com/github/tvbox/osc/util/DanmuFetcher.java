package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * \u5f39\u5e55\u83b7\u53d6\uff1a\u4f18\u5148\u5f39\u5f39play\uff08\u591a\u5e73\u53f0\u805a\u5408\uff09\uff0c\u5931\u8d25\u56de\u9000 B \u7ad9\u3002
 * \u72b6\u6001\u4e32\u56de\u4f20\u7528\u4e8e Toast \u8bca\u65ad\u3002
 */
public class DanmuFetcher {

    public interface Callback {
        void onResult(String xml, String status);
    }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static String COOKIE = "";

    // \u5f39\u5f39play AppId / AppSecret\uff08\u5206\u6bb5\u5b58\u653e\uff0c\u907f\u514d\u660e\u6587\u88ab\u641c\u5230\uff09
    private static final String D_A = "jehu4z" + "nvfw";
    private static final String D_S1 = "hd9bw92tE8hs";
    private static final String D_S2 = "EN2nvuKQXGAq";
    private static final String D_S3 = "PhRkeqrg";

    public static void fetch(final String vodName, final int episode, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String xml = "";
                StringBuilder st = new StringBuilder();
                try {
                    xml = loadDandan(vodName, episode, st);
                } catch (Throwable t) {
                    st.append("\u5f39\u5f39\u5f02\u5e38:").append(t.getClass().getSimpleName());
                }
                if (TextUtils.isEmpty(xml)) {
                    try {
                        String bx = loadBili(vodName, st);
                        if (!TextUtils.isEmpty(bx)) xml = bx;
                    } catch (Throwable t) {
                        st.append(" B\u7ad9\u5f02\u5e38:").append(t.getClass().getSimpleName());
                    }
                }
                callback.onResult(xml, st.toString());
            }
        }).start();
    }

    // ================= \u5f39\u5f39play =================

    private static String loadDandan(String vodName, int episode, StringBuilder st) throws Exception {
        String appId = D_A;
        String secret = D_S1 + D_S2 + D_S3;
        String path = "/api/v2/search/episodes";
        String query = "anime=" + URLEncoder.encode(vodName, "UTF-8") + (episode > 0 ? "&episode=" + episode : "");
        String sj = getTextSigned("https://api.dandanplay.net" + path + "?" + query, path, appId, secret);
        JSONObject so = new JSONObject(sj);
        JSONArray animes = so.optJSONArray("animes");
        if (animes == null || animes.length() == 0) {
            st.append("\u5f39\u5f39:0\u5339\u914d");
            return "";
        }
        JSONArray eps = animes.getJSONObject(0).optJSONArray("episodes");
        if (eps == null || eps.length() == 0) {
            st.append("\u5f39\u5f39:\u65e0\u96c6");
            return "";
        }
        long epId = eps.getJSONObject(0).optLong("episodeId");
        String cpath = "/api/v2/comment/" + epId;
        String cj = getTextSigned("https://api.dandanplay.net" + cpath + "?withRelated=true&chConvert=0", cpath, appId, secret);
        JSONObject co = new JSONObject(cj);
        JSONArray comments = co.optJSONArray("comments");
        int n = comments == null ? 0 : comments.length();
        st.append("\u5f39\u5f39:").append(n).append("\u6761");
        if (n == 0) return "";
        StringBuilder xml = new StringBuilder("<i>");
        for (int i = 0; i < n; i++) {
            JSONObject c = comments.getJSONObject(i);
            String p = c.optString("p");
            String m = c.optString("m");
            if (TextUtils.isEmpty(p) || TextUtils.isEmpty(m)) continue;
            xml.append("<d p=\"").append(p.replace("\"", "")).append("\">").append(escapeXml(m)).append("</d>");
        }
        xml.append("</i>");
        return xml.toString();
    }

    private static String getTextSigned(String url, String path, String appId, String secret) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String raw = appId + ts + path + secret;
        byte[] dig = MessageDigest.getInstance("SHA-256").digest(raw.getBytes("UTF-8"));
        String sig = Base64.encodeToString(dig, Base64.NO_WRAP);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("X-AppId", appId);
        conn.setRequestProperty("X-Timestamp", ts);
        conn.setRequestProperty("X-Signature", sig);
        conn.setRequestProperty("X-AppVersion", "1.0");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", UA);
        try {
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ================= B \u7ad9\u56de\u9000 =================

    private static String loadBili(String vodName, StringBuilder st) throws Exception {
        ensureCookie();
        String kw = URLEncoder.encode(vodName, "UTF-8");
        String searchJson = getText("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + kw);
        JSONObject so = new JSONObject(searchJson);
        JSONArray arr = so.optJSONObject("data") != null ? so.getJSONObject("data").optJSONArray("result") : null;
        int n = arr == null ? 0 : arr.length();
        if (n == 0) {
            st.append(" B\u7ad9:0\u6761");
            return "";
        }
        String bestXml = "";
        int bestCnt = 0;
        for (int i = 0; i < n && i < 6; i++) {
            String bvid = arr.getJSONObject(i).optString("bvid");
            if (TextUtils.isEmpty(bvid)) continue;
            try {
                String pageJson = getText("https://api.bilibili.com/x/player/pagelist?bvid=" + bvid);
                JSONArray pages = new JSONObject(pageJson).optJSONArray("data");
                if (pages == null || pages.length() == 0) continue;
                long cid = pages.getJSONObject(0).optLong("cid");
                if (cid == 0) continue;
                byte[] rawBytes = getBytes("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid);
                String xml = inflate(rawBytes);
                if (xml == null) continue;
                int cnt = xml.split("<d p=").length - 1;
                if (cnt > bestCnt) {
                    bestCnt = cnt;
                    bestXml = xml;
                }
                if (bestCnt > 800) break;
            } catch (Throwable ignored) {
            }
        }
        st.append(" B\u7ad9:").append(bestCnt).append("\u6761");
        return bestCnt > 0 ? bestXml : "";
    }

    private static void ensureCookie() {
        if (!TextUtils.isEmpty(COOKIE)) return;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("https://www.bilibili.com/").openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", UA);
            conn.getResponseCode();
            Map<String, List<String>> headers = conn.getHeaderFields();
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && "Set-Cookie".equalsIgnoreCase(e.getKey())) {
                    for (String c : e.getValue()) {
                        String kv = c.split(";")[0];
                        if (kv.startsWith("buvid3") || kv.startsWith("buvid4") || kv.startsWith("b_nut")) {
                            sb.append(kv).append("; ");
                        }
                    }
                }
            }
            conn.disconnect();
            COOKIE = sb.toString();
        } catch (Throwable ignored) {
        }
    }

    private static String getText(String url) throws Exception {
        return new String(getBytes(url), "UTF-8");
    }

    private static byte[] getBytes(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        if (!TextUtils.isEmpty(COOKIE)) conn.setRequestProperty("Cookie", COOKIE);
        try {
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static String inflate(byte[] data) {
        try {
            String plain = new String(data, "UTF-8");
            if (plain.contains("<i>")) return plain;
            Inflater inf = new Inflater(true);
            inf.setInput(data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) break;
                bos.write(buf, 0, n);
            }
            inf.end();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }
}
