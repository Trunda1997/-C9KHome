package de.tcl.c9khome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Small shared worker pool; image decoding, providers and networking never run on the UI thread. */
public final class AsyncImageLoader implements AutoCloseable {
    private static final int MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private final ThreadPoolExecutor workers;
    private volatile boolean closed;
    private final AtomicInteger generation = new AtomicInteger();

    public AsyncImageLoader(Context context) {
        this.context = context.getApplicationContext();
        AtomicInteger number = new AtomicInteger();
        workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), task -> {
                    Thread thread = new Thread(task, "c9k-images-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        workers.allowCoreThreadTimeOut(true);
    }

    public void loadIcon(LaunchableApps.Entry entry, ImageView target) {
        String key = "app:" + entry.packageName;
        target.setImageResource(android.R.drawable.sym_def_app_icon);
        request(key, target, () -> {
            Drawable icon = entry.loadIcon(context.getPackageManager());
            Bitmap result = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            icon.setBounds(0, 0, result.getWidth(), result.getHeight());
            icon.draw(canvas);
            return result;
        });
    }

    public void load(Uri uri, ImageView target) {
        if (uri == null) {
            target.setTag(null);
            target.setImageDrawable(null);
            return;
        }
        target.setImageDrawable(null);
        request("uri:" + uri, target, () -> readImage(uri));
    }

    private interface BitmapSource { Bitmap load() throws Exception; }

    private void request(String key, ImageView target, BitmapSource source) {
        target.setTag(key);
        Bitmap hit;
        synchronized (cache) { hit = cache.get(key); }
        if (hit != null) {
            target.setImageBitmap(hit);
            return;
        }
        enqueue(key, new WeakReference<>(target), source, 0);
    }

    private void enqueue(String key, WeakReference<ImageView> reference, BitmapSource source, int retry) {
        if (closed) return;
        final int requestedGeneration = generation.get();
        try {
            workers.execute(() -> {
                ImageView image = reference.get();
                if (image == null || !key.equals(image.getTag()) || closed) return;
                Bitmap result;
                try { result = source.load(); }
                catch (Exception | OutOfMemoryError unavailable) { return; }
                if (result == null || closed || requestedGeneration != generation.get()) return;
                synchronized (cache) { cache.put(key, result); }
                main.post(() -> {
                    ImageView current = reference.get();
                    if (!closed && requestedGeneration == generation.get()
                            && current != null && key.equals(current.getTag())) current.setImageBitmap(result);
                });
            });
        } catch (RejectedExecutionException busy) {
            // Bounded retries keep a large installed-app list from allocating
            // an unbounded worker queue or blocking focus on the main thread.
            if (retry < 8 && !closed) main.postDelayed(() -> {
                ImageView current = reference.get();
                if (current != null && current.isAttachedToWindow() && key.equals(current.getTag())) {
                    enqueue(key, reference, source, retry + 1);
                }
            }, 160L + retry * 80L);
        }
    }

    private Bitmap readImage(Uri uri) throws Exception {
        byte[] bytes;
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
            HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            try {
                if (connection.getContentLengthLong() > MAX_DOWNLOAD_BYTES) return null;
                try (InputStream input = connection.getInputStream()) { bytes = readBounded(input); }
            } finally { connection.disconnect(); }
        } else if ("content".equalsIgnoreCase(scheme) || "android.resource".equalsIgnoreCase(scheme)) {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) return null;
                bytes = readBounded(input);
            }
        } else return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > 1280
                || bounds.outHeight / options.inSampleSize > 960
                || (long) (bounds.outWidth / options.inSampleSize)
                        * (bounds.outHeight / options.inSampleSize) > 1024 * 1024) {
            options.inSampleSize *= 2;
        }
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private byte[] readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted() || output.size() + read > MAX_DOWNLOAD_BYTES) {
                throw new java.io.IOException("Image unavailable or too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public void clearPackage(String packageName) {
        generation.incrementAndGet();
        synchronized (cache) {
            for (String key : cache.snapshot().keySet()) {
                // URI artwork can originate in an uninstalled app's provider.
                // Evicting URI entries together avoids retaining its artwork.
                if (key.equals("app:" + packageName) || key.startsWith("uri:")) cache.remove(key);
            }
        }
    }

    @Override public void close() {
        closed = true;
        workers.shutdownNow();
        main.removeCallbacksAndMessages(null);
        synchronized (cache) { cache.evictAll(); }
    }
}
