package com.github.tvbox.osc.util;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SourceSpeedTester {

    public interface Callback {
        void onDone(Map<String, Long> delays);
    }

    public interface Progress {
        void onProgress(int doneCount, int total, Map<String, Long> partial);
    }

    public static void test(List<SourceBean> sites, final Callback callback, final Progress progress) {
        final Map<String, Long> delays = new ConcurrentHashMap<>();
        final ExecutorService pool = Executors.newFixedThreadPool(32);
        final AtomicInteger done = new AtomicInteger();
        final int total = sites.size();
        for (final SourceBean sb : sites) {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    delays.put(sb.getKey(), measure(sb));
                    int n = done.incrementAndGet();
                    if (progress != null && (n % 6 == 0 || n == total)) {
                        progress.onProgress(n, total, delays);
                    }
                }
            });
        }
        pool.shutdown();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pool.awaitTermination(75, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                callback.onDone(delays);
            }
        }).start();
    }

    private static long measure(SourceBean sb) {
        String api = sb.getApi();
        if (api != null && api.startsWith("http")) return measureHttp(api);
        return measureSpider(sb);
    }

    private static long measureHttp(String api) {
        long t = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(api).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "okhttp/3.15");
            conn.getResponseCode();
            return System.currentTimeMillis() - t;
        } catch (Exception e) {
            return -1L;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static long measureSpider(final SourceBean sb) {
        ExecutorService single = Executors.newSingleThreadExecutor();
        final long t = System.currentTimeMillis();
        Future<Long> fut = single.submit(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                Spider spider = ApiConfig.get().getCSP(sb);
                String result = spider.searchContent("\u7535\u5f71", true);
                if (result == null) return -1L;
                return System.currentTimeMillis() - t;
            }
        });
        try {
            return fut.get(6, TimeUnit.SECONDS);
        } catch (Exception e) {
            fut.cancel(true);
            return -1L;
        } finally {
            single.shutdownNow();
        }
    }
}
