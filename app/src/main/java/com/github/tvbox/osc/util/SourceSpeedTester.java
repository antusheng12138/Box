package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.SourceBean;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SourceSpeedTester {

    public interface Callback {
        void onDone(Map<String, Long> delays);
    }

    public static void test(List<SourceBean> sites, final Callback callback) {
        final Map<String, Long> delays = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(24);
        for (final SourceBean sb : sites) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    delays.put(sb.getKey(), measure(sb.getApi()));
                }
            });
        }
        pool.shutdown();
        final ExecutorService p = pool;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    p.awaitTermination(15, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                callback.onDone(delays);
            }
        }).start();
    }

    private static long measure(String api) {
        if (api == null || !api.startsWith("http")) return -2L;
        long t = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(api).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "okhttp/3.15");
            conn.getResponseCode();
            conn.getInputStream().close();
            return System.currentTimeMillis() - t;
        } catch (Exception e) {
            return -1L;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
