package com.github.tvbox.osc.util;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.zip.Inflater;

/**
 * \u7f51\u7edc\u5f39\u5e55\uff1a\u641c B \u7ad9 -> cid -> \u5f39\u5e55 xml\uff08\u89e3 raw-deflate\uff09
 */
public class DanmuFetcher {

    public interface Callback {
        void onResult(String xml);
    }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    public static void fetch(final String vodName, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = "";
                try {
                    result = load(vodName);
                } catch (Throwable ignored) {
                }
                callback.onResult(result);
            }
        }).start();
    }

    private static String load(String vodName) throws Exception {
        String kw = URLEncoder.encode(vodName, "UTF-8");
        String searchJson = getText("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + kw);
        JSONObject so = new JSONObject(searchJson);
        JSONArray arr = so.optJSONObject("data") != null ? so.getJSONObject("data").optJSONArray("result") : null;
        if (arr == null || arr.length() == 0) return "";
        String bvid = null;
        for (int i = 0; i < arr.length() && i < 6; i++) {
            String title = arr.getJSONObject(i).optString("title").replaceAll("<[^>]+>", "");
            if (title.contains(vodName) || vodName.contains(title)) {
                bvid = arr.getJSONObject(i).optString("bvid");
                break;
            }
        }
        if (TextUtils.isEmpty(bvid)) bvid = arr.getJSONObject(0).optString("bvid");
        if (TextUtils.isEmpty(bvid)) return "";
        String pageJson = getText("https://api.bilibili.com/x/player/pagelist?bvid=" + bvid);
        JSONArray pages = new JSONObject(pageJson).optJSONArray("data");
        if (pages == null || pages.length() == 0) return "";
        long cid = pages.getJSONObject(0).optLong("cid");
        if (cid == 0) return "";
        byte[] rawBytes = getBytes("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid);
        String xml = inflate(rawBytes);
        return xml != null && xml.contains("<d p=") ? xml : "";
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
