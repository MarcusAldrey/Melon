package com.forcetower.sagres.executor;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DefaultTaskExecutor extends TaskExecutor {

    private final Object mLock = new Object();

    private final ExecutorService mDiskIO = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private static final String THREAD_NAME_STEM = "unes_disk_io_%d";

        private final AtomicInteger mThreadId = new AtomicInteger(0);

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r);
            t.setName(String.format(THREAD_NAME_STEM, mThreadId.getAndIncrement()));
            return t;
        }
    });

    private final ExecutorService mNetworkIO = Executors.newFixedThreadPool(8, new ThreadFactory() {
        private static final String THREAD_NAME_STEM = "unes_net_io_%d";

        private final AtomicInteger mThreadId = new AtomicInteger(0);

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r);
            t.setName(String.format(THREAD_NAME_STEM, mThreadId.getAndIncrement()));
            return t;
        }
    });

    @Nullable
    private volatile Handler mMainHandler;

    @Override
    public void executeOnDiskIO(@NonNull Runnable runnable) {
        mDiskIO.execute(runnable);
    }

    @Override
    public void executeOnNetworkIO(@NonNull Runnable runnable) {
        mNetworkIO.execute(runnable);
    }

    @Override
    public void postToMainThread(@NonNull Runnable runnable) {
        if (mMainHandler == null) {
            synchronized (mLock) {
                if (mMainHandler == null) {
                    mMainHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        //noinspection ConstantConditions
        mMainHandler.post(runnable);
    }

    @Override
    public boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }
}