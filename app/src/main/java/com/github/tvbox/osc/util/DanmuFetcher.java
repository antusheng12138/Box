package com.github.tvbox.osc.util;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * B \u7ad9\u5f39\u5e55\uff1a\u641c\u7d22\u591a\u9875 -> \u524d N \u4e2a\u5019\u9009\u5168\u90e8\u62c9\u5f39\u5e55 -> \u5408\u5e76\u53bb\u91cd
 */
public class DanmuFetcher {

    public interface Callback {
        void onResult(String xml, String status);
    }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String COOKIE = "";

    public static void fetch(final String vodName, final int episode, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String xml = "";
                StringBuilder st = new StringBuilder();
                try {
                    xml = loadBili(vodName, st);
                } catch (Throwable t) {
                    st.append("\u5f02\u5e38:").append(t.getClass().getSimpleName()).append(":").append(t.getMessage());
                }
                callback.onResult(xml, st.toString());
            }
        }).start();
    }

    private static String loadBili(String vodName, StringBuilder st) throws Exception {
        String cookie = initCookie();
        String kw = URLEncoder.encode(vodName, "UTF-8");
        Set<String> keys = new HashSet<String>();
        StringBuilder xml = new StringBuilder();
        int blocks = 0;
        int total = 0;
        int tried = 0;

        for (int page = 1; page <= 2; page++) {
            String sj = getText("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + kw + "&page=" + page, cookie, null);
            JSONObject so = new JSONObject(sj);
            JSONArray arr = so.optJSONObject("data") != null ? so.getJSONObject("data").optJSONArray("result") : null;
            if (arr == null || arr.length() == 0) break;
            for (int i = 0; i < arr.length() && i < 6; i++) {
                String bvid = arr.getJSONObject(i).optString("bvid");
                if (TextUtils.isEmpty(bvid)) continue;
                tried++;
                try {
                    String pageJson = getText("https://api.bilibili.com/x/player/pagelist?bvid=" + bvid, cookie, null);
                    JSONArray pages = new JSONObject(pageJson).optJSONArray("data");
                    if (pages == null || pages.length() == 0) continue;
                    long cid = pages.getJSONObject(0).optLong("cid");
                    if (cid == 0) continue;
                    byte[] rawBytes = getBytes("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid, cookie, "https://www.bilibili.com/");
                    String part = inflate(rawBytes);
                    if (part == null) continue;
                    String[] lines = part.split("<d p=");
                    int added = 0;
                    for (int k = 1; k < lines.length; k++) {
                        String seg = lines[k];
                        int end = seg.indexOf("</d>");
                        if (end < 0) continue;
                        String key = seg.substring(0, Math.min(48, end));
                        if (keys.contains(key)) continue;
                        keys.add(key);
                        xml.append("<d p=").append(seg.substring(0, end)).append("</d>");
                        added++;
                    }
                    if (added > 0) blocks++;
                    total += added;
                } catch (Throwable ignored) {
                }
            }
        }
        st.append("B\u7ad9:").append(blocks).append("\u4e2a\u89c6\u9891\u5408\u5e76 ").append(total).append("\u6761/\u8bd5\u4e86").append(tried);
        if (total == 0) return "";
        return "<i>" + xml + "</i>";
    }

    private static String initCookie() {
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
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String getText(String url, String cookie, String referer) throws Exception {
        return new String(getBytes(url, cookie, referer), "UTF-8");
    }

    private static byte[] getBytes(String url, String cookie, String referer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Referer", referer != null ? referer : "https://www.bilibili.com/");
        if (!TextUtils.isEmpty(cookie)) conn.setRequestProperty("Cookie", cookie);
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
