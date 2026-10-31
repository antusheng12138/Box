package com.github.tvbox.osc.util;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * \u5f39\u5e55\uff1aB \u7ad9\uff08\u591a\u89c6\u9891\u5408\u5e76\uff09+ A \u7ad9\uff08AcFun\uff09\u4e24\u6e90\u5408\u5e76\u53bb\u91cd
 */
public class DanmuFetcher {

    public interface Callback {
        void onResult(String xml, String status);
    }

    private static final String UA_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String UA_AC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36";

    public static void fetch(final String vodName, final int episode, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder st = new StringBuilder();
                Set<String> seen = new HashSet<String>();
                StringBuilder items = new StringBuilder();
                int total = 0;
                try {
                    total += collect("B\u7ad9", loadBili(vodName, st), seen, items);
                } catch (Throwable t) {
                    st.append(" B\u7ad9\u5f02\u5e38:").append(t.getClass().getSimpleName());
                }
                try {
                    total += collect("A\u7ad9", loadAcfun(vodName, st), seen, items);
                } catch (Throwable t) {
                    st.append(" A\u7ad9\u5f02\u5e38:").append(t.getClass().getSimpleName());
                }
                String xml = total > 0 ? "<i>" + items + "</i>" : "";
                callback.onResult(xml, st.toString() + " \u5171" + total + "\u6761");
            }
        }).start();
    }

    private static int collect(String tag, String part, Set<String> seen, StringBuilder items) {
        if (TextUtils.isEmpty(part)) return 0;
        int n = 0;
        String[] arr = part.split("</d>");
        for (String seg : arr) {
            int s = seg.indexOf("<d p=");
            if (s < 0) continue;
            String one = seg.substring(s) + "</d>";
            String key = one.length() > 52 ? one.substring(0, 52) : one;
            if (seen.contains(key)) continue;
            seen.add(key);
            items.append(one);
            n++;
        }
        return n;
    }

    // ================= B \u7ad9 =================

    private static String loadBili(String vodName, StringBuilder st) throws Exception {
        String cookie = initBiliCookie();
        String kw = URLEncoder.encode(vodName, "UTF-8");
        StringBuilder xml = new StringBuilder();
        int blocks = 0;
        for (int page = 1; page <= 2; page++) {
            String sj = getText("https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" + kw + "&page=" + page, cookie, "https://www.bilibili.com/");
            JSONObject so = new JSONObject(sj);
            JSONArray arr = so.optJSONObject("data") != null ? so.getJSONObject("data").optJSONArray("result") : null;
            if (arr == null || arr.length() == 0) break;
            for (int i = 0; i < arr.length() && i < 5; i++) {
                String bvid = arr.getJSONObject(i).optString("bvid");
                if (TextUtils.isEmpty(bvid)) continue;
                try {
                    String pageJson = getText("https://api.bilibili.com/x/player/pagelist?bvid=" + bvid, cookie, "https://www.bilibili.com/");
                    JSONArray pages = new JSONObject(pageJson).optJSONArray("data");
                    if (pages == null || pages.length() == 0) continue;
                    long cid = pages.getJSONObject(0).optLong("cid");
                    if (cid == 0) continue;
                    byte[] rawBytes = getBytes("https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid, cookie, "https://www.bilibili.com/");
                    String part = inflate(rawBytes);
                    if (part == null) continue;
                    int idx = part.indexOf("<d p=");
                    if (idx < 0) continue;
                    int end = part.lastIndexOf("</d>");
                    if (end < 0) continue;
                    xml.append(part.substring(idx, end + 4));
                    blocks++;
                    if (xml.length() > 400000) break;
                } catch (Throwable ignored) {
                }
            }
        }
        st.append("B\u7ad9:").append(blocks).append("\u89c6\u9891");
        return xml.toString();
    }

    private static String initBiliCookie() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("https://www.bilibili.com/").openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", UA_PC);
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

    // ================= A \u7ad9\uff08AcFun\uff09 =================

    private static String loadAcfun(String vodName, StringBuilder st) throws Exception {
        String kw = URLEncoder.encode(vodName, "UTF-8");
        String html = getText("https://www.acfun.cn/search?keyword=" + kw, "", "https://www.acfun.cn/");
        Matcher m = Pattern.compile("/v/ac(\\d+)").matcher(html);
        List<String> acs = new ArrayList<String>();
        while (m.find() && acs.size() < 3) {
            if (!acs.contains(m.group(1))) acs.add(m.group(1));
        }
        if (acs.isEmpty()) {
            st.append(" A\u7ad9:\u65e0\u5339\u914d");
            return "";
        }
        StringBuilder xml = new StringBuilder();
        int total = 0;
        for (String ac : acs) {
            String page = getText("https://www.acfun.cn/v/ac" + ac, "", "https://www.acfun.cn/");
            Matcher vm = Pattern.compile("\"currentVideoId\"\\s*:\\s*(\\d+)").matcher(page);
            if (!vm.find()) continue;
            total += fetchAcfunDanmaku(vm.group(1), xml);
            if (total > 600) break;
        }
        st.append(" A\u7ad9:").append(total).append("\u6761");
        return xml.toString();
    }

    private static int fetchAcfunDanmaku(String videoId, StringBuilder xml) {
        int total = 0;
        String pcursor = "0";
        for (int page = 0; page < 8 && !"no_more".equals(pcursor); page++) {
            try {
                String form = "resourceId=" + videoId + "&resourceType=9&enableAdvanced=true&pcursor=" + pcursor + "&count=200&sortType=1&asc=false";
                String resp = postForm("https://www.acfun.cn/rest/pc-direct/new-danmaku/list", form);
                JSONObject o = new JSONObject(resp);
                JSONArray arr = o.optJSONArray("danmakus");
                if (arr == null) break;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject d = arr.getJSONObject(i);
                    String text = d.optString("body");
                    if (TextUtils.isEmpty(text)) continue;
                    long pos = d.optLong("position");
                    int mode = d.optInt("mode", 1);
                    int size = d.optInt("size", 25);
                    int color = d.optInt("color", 16777215);
                    double sec = pos / 1000.0;
                    xml.append("<d p=\"").append(sec).append(",").append(mode).append(",").append(size).append(",").append(color).append(",0,0,0,0\">").append(escapeXml(text)).append("</d>");
                    total++;
                }
                pcursor = o.optString("pcursor", "no_more");
            } catch (Throwable t) {
                break;
            }
        }
        return total;
    }

    private static String postForm(String url, String form) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", UA_AC);
        conn.setRequestProperty("Referer", "https://www.acfun.cn/");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        OutputStream os = conn.getOutputStream();
        os.write(form.getBytes("UTF-8"));
        os.close();
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

    // ================= \u5de5\u5177 =================

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String getText(String url, String cookie, String referer) throws Exception {
        return new String(getBytes(url, cookie, referer), "UTF-8");
    }

    private static byte[] getBytes(String url, String cookie, String referer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", UA_PC);
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
