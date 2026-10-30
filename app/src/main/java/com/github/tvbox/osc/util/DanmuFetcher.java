package com.github.tvbox.osc.util;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * \u7f51\u7edc\u5f39\u5e55\uff1a\u641c B \u7ad9 -> cid -> \u5f39\u5e55 xml\uff08\u89e3 raw-deflate\uff09\uff0c\u5e26\u8bca\u65ad\u72b6\u6001
 */
public class DanmuFetcher {

    public interface Callback {
        void onResult(String xml, String status);
    }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static String COOKIE = "";

    public static void fetch(final String vodName, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String xml = "";
                StringBuilder st = new StringBuilder();
                try {
                    xml = load(vodName, st);
                } catch (Throwable t) {
                    st.append("\u5f02\u5e38:").append(t.getClass().getSimpleName()).append(":").append(t.getMessage());
                }
                callback.onResult(xml, st.toString());
            }
        }).start();
    }

    private static String load(String vodName, StringBuilder st) throws Exception {
        ensureCookie();
        String kw = URLEncoder.encode(vodName, "UTF-8");
        String searchJson = getText("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + kw);
        JSONObject so = new JSONObject(searchJson);
        int code = so.optInt("code", -999);
        JSONArray arr = so.optJSONObject("data") != null ? so.getJSONObject("data").optJSONArray("result") : null;
        int n = arr == null ? 0 : arr.length();
        st.append("\u641c\u7d22code=").append(code).append(" \u6761\u6570=").append(n);
        if (n == 0) return "";
        String bvid = null;
        for (int i = 0; i < n && i < 8; i++) {
            String title = arr.getJSONObject(i).optString("title").replaceAll("<[^>]+>", "");
            if (title.contains(vodName) || vodName.contains(title)) {
                bvid = arr.getJSONObject(i).optString("bvid");
                break;
            }
        }
        if (TextUtils.isEmpty(bvid)) bvid = arr.getJSONObject(0).optString("bvid");
        st.append(" bv=").append(bvid);
        if (TextUtils.isEmpty(bvid)) return "";
        String pageJson = getText("https://api.bilibili.com/x/player/pagelist?bvid=" + bvid);
        JSONArray pages = new JSONObject(pageJson).optJSONArray("data");
        if (pages == null || pages.length() == 0) {
            st.append(" \u65e0\u5206P");
            return "";
        }
        long cid = pages.getJSONObject(0).optLong("cid");
        st.append(" cid=").append(cid);
        if (cid == 0) return "";
        byte[] rawBytes = getBytes("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid);
        String xml = inflate(rawBytes);
        if (xml == null) {
            st.append(" \u89e3\u538b\u5931\u8d25");
            return "";
        }
        int cnt = xml.split("<d p=").length - 1;
        st.append(" \u5f39\u5e55=").append(cnt);
        return cnt > 0 ? xml : "";
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
