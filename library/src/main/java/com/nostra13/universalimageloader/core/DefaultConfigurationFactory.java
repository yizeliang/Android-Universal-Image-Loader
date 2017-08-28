/*******************************************************************************
 * Copyright 2011-2014 Sergey Tarasevich
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.nostra13.universalimageloader.core;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.nostra13.universalimageloader.cache.disc.DiskCache;
import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache;
import com.nostra13.universalimageloader.cache.disc.impl.ext.LruDiskCache;
import com.nostra13.universalimageloader.cache.disc.naming.FileNameGenerator;
import com.nostra13.universalimageloader.cache.disc.naming.HashCodeFileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.MemoryCache;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.assist.deque.LIFOLinkedBlockingDeque;
import com.nostra13.universalimageloader.core.decode.BaseImageDecoder;
import com.nostra13.universalimageloader.core.decode.ImageDecoder;
import com.nostra13.universalimageloader.core.display.BitmapDisplayer;
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;
import com.nostra13.universalimageloader.core.download.ImageDownloader;
import com.nostra13.universalimageloader.utils.L;
import com.nostra13.universalimageloader.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一些常用options参数的构造方法
 * Factory for providing of default options for {@linkplain ImageLoaderConfiguration configuration}
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.5.6
 */
public class DefaultConfigurationFactory {

    /**
     * 构建任务线程池
     * Creates default implementation of task executor
     *
     * @return ThreadPoolExecutor
     */
    public static Executor createExecutor(int threadPoolSize, int threadPriority,
                                          QueueProcessingType tasksProcessingType) {
        boolean lifo = tasksProcessingType == QueueProcessingType.LIFO;
        //根据队列类型创建不同的实现类,处理任务
        BlockingQueue<Runnable> taskQueue =
                lifo ? new LIFOLinkedBlockingDeque<Runnable>() : new LinkedBlockingQueue<Runnable>();
        return new ThreadPoolExecutor(threadPoolSize, threadPoolSize,
                0L, TimeUnit.MILLISECONDS, taskQueue,
                createThreadFactory(threadPriority, "uil-pool-"));
    }

    /**
     * 创建默认的 ThreadPoolExecutor
     * Creates default implementation of task distributor
     */
    public static Executor createTaskDistributor() {
        return Executors.newCachedThreadPool(createThreadFactory(Thread.NORM_PRIORITY, "uil-pool-d-"));
    }

    /**
     * Creates {@linkplain HashCodeFileNameGenerator default implementation} of FileNameGenerator
     */
    public static FileNameGenerator createFileNameGenerator() {
        return new HashCodeFileNameGenerator();
    }

    /**
     * 创建默认的硬盘缓存
     * Creates default implementation of {@link DiskCache} depends on incoming parameters
     */
    public static DiskCache createDiskCache(Context context, FileNameGenerator diskCacheFileNameGenerator,
                                            long diskCacheSize, int diskCacheFileCount) {
        File reserveCacheDir = createReserveDiskCacheDir(context);
        if (diskCacheSize > 0 || diskCacheFileCount > 0) {
            File individualCacheDir = StorageUtils.getIndividualCacheDirectory(context);
            try {
                return new LruDiskCache(individualCacheDir, reserveCacheDir, diskCacheFileNameGenerator,
                        diskCacheSize,
                        diskCacheFileCount);
            } catch (IOException e) {
                L.e(e);
                // continue and create unlimited cache
            }
        }
        File cacheDir = StorageUtils.getCacheDirectory(context);
        return new UnlimitedDiskCache(cacheDir, reserveCacheDir, diskCacheFileNameGenerator);
    }

    /**
     * 创建默认硬盘缓存文件夹
     * Creates reserve disk cache folder which will be used if primary disk cache folder becomes unavailable
     */
    private static File createReserveDiskCacheDir(Context context) {
        File cacheDir = StorageUtils.getCacheDirectory(context, false);
        File individualDir = new File(cacheDir, "uil-images");
        if (individualDir.exists() || individualDir.mkdir()) {
            cacheDir = individualDir;
        }
        return cacheDir;
    }

    /**
     * 默认内存缓存类
     * Creates default implementation of {@link MemoryCache} - {@link LruMemoryCache}<br />
     * Default cache size = 1/8 of available app memory.
     */
    public static MemoryCache createMemoryCache(Context context, int memoryCacheSize) {
        if (memoryCacheSize == 0) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            int memoryClass = am.getMemoryClass();
            if (hasHoneycomb() && isLargeHeap(context)) {
                memoryClass = getLargeMemoryClass(am);
            }
            memoryCacheSize = 1024 * 1024 * memoryClass / 8;
        }
        return new LruMemoryCache(memoryCacheSize);
    }

    /**
     * Android 版本是否大于等于3.0
     *
     * @return
     */
    private static boolean hasHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    /**
     * 是否不限制内存
     *
     * @param context
     * @return
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static boolean isLargeHeap(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static int getLargeMemoryClass(ActivityManager am) {
        return am.getLargeMemoryClass();
    }

    /**
     * 默认下载器
     * Creates default implementation of {@link ImageDownloader} - {@link BaseImageDownloader}
     */
    public static ImageDownloader createImageDownloader(Context context) {
        return new BaseImageDownloader(context);
    }

    /**
     * 默认解码器
     * Creates default implementation of {@link ImageDecoder} - {@link BaseImageDecoder}
     */
    public static ImageDecoder createImageDecoder(boolean loggingEnabled) {
        return new BaseImageDecoder(loggingEnabled);
    }

    /**
     * 默认displayer
     * Creates default implementation of {@link BitmapDisplayer} - {@link SimpleBitmapDisplayer}
     */
    public static BitmapDisplayer createBitmapDisplayer() {
        return new SimpleBitmapDisplayer();
    }

    /**
     * 创建线程辅助方法
     * Creates default implementation of {@linkplain ThreadFactory thread factory} for task executor
     */
    private static ThreadFactory createThreadFactory(int threadPriority, String threadNamePrefix) {
        return new DefaultThreadFactory(threadPriority, threadNamePrefix);
    }

    /**
     * 线程工厂类
     */
    private static class DefaultThreadFactory implements ThreadFactory {
        //AtomicInteger，一个提供原子操作的Integer的类。
        // 在Java语言中，++i和i++操作并不是线程安全的，
        // 在使用的时候，不可避免的会用到synchronized关键字。
        // 而AtomicInteger则通过一种线程安全的加减操作接口。
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        /**
         * 线程组
         */
        private final ThreadGroup group;

        private final AtomicInteger threadNumber = new AtomicInteger(1);
        /**
         * 线程名字前缀
         */
        private final String namePrefix;
        /**
         * 线程级别
         */
        private final int threadPriority;

        /**
         * 初始化配置
         * @param threadPriority
         * @param threadNamePrefix
         */
        DefaultThreadFactory(int threadPriority, String threadNamePrefix) {
            this.threadPriority = threadPriority;
            group = Thread.currentThread().getThreadGroup();
            namePrefix = threadNamePrefix + poolNumber.getAndIncrement() + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            /**
             * 根据配置生成一个线程
             */
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            //是否是守护线程,设置为false
            if (t.isDaemon()) t.setDaemon(false);
            t.setPriority(threadPriority);
            return t;
        }
    }
}
