## ImageLoader源码解析(二) 缓存实现


### 1 缓存类的初始化

在ImageLoaderConfiguration.Build中,有内存缓存和硬盘缓存的设置方法

一般来说,如果咱们没有设置自己的缓存实现类的话,会走下面这个方法

- ImageLoaderConfiguration.Builder#initEmptyFieldsWithDefaultValues
```java
private void initEmptyFieldsWithDefaultValues() {

            //创建默认硬盘缓存
            if (diskCache == null) {
                if (diskCacheFileNameGenerator == null) {
                    diskCacheFileNameGenerator = DefaultConfigurationFactory.createFileNameGenerator();
                }
                diskCache = DefaultConfigurationFactory
                        .createDiskCache(context, diskCacheFileNameGenerator, diskCacheSize, diskCacheFileCount);
            }
            /**
             * 创建默认内存缓存
             */
            if (memoryCache == null) {
                memoryCache = DefaultConfigurationFactory.createMemoryCache(context, memoryCacheSize);
            }
            if (denyCacheImageMultipleSizesInMemory) {
                //如果需要缓存多种尺寸
                memoryCache = new FuzzyKeyMemoryCache(memoryCache, MemoryCacheUtils.createFuzzyKeyComparator());
            }
    }
```
从上面就可以看出,如果没有设置自己的缓存类,那么Imagloader会通过DefaultConfigurationFactory帮我们创建一个默认的实现类.

项目中的代码我已经加上注释,这里就不再书写太多的代码

- DefaultConfigurationFactory
```java

  /**
     * 创建默认的硬盘缓存
     */
    public static DiskCache createDiskCache(Context context, FileNameGenerator diskCacheFileNameGenerator,
                                            long diskCacheSize, int diskCacheFileCount) {
        File reserveCacheDir = createReserveDiskCacheDir(context);
        if (diskCacheSize > 0 || diskCacheFileCount > 0) {
		//获取缓存文件夹
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

```

通过上面的代码,很轻易就能看到具体缓存实现类
这里只对LruMemoryCache类进行解析,其他的相差不大

#### 2 MemoryCache


先看一下抽闲接口MemoryCache

```java
public interface MemoryCache {
	/**
	 * Puts value into cache by key
	 *
	 * @return <b>true</b> - if value was put into cache successfully, <b>false</b> - if value was <b>not</b> put into
	 * cache
	 */
	boolean put(String key, Bitmap value);

	/** Returns value by key. If there is no value for key then null will be returned. */
	Bitmap get(String key);

	/** Removes item by key */
	Bitmap remove(String key);

	/** Returns all keys of cache */
	Collection<String> keys();

	/** Remove all items from cache */
	void clear();
}

```
很简单,关键方法 put和get,操作就像map一样,其实内部就是一个map


#### 3 LruMemoryCache

- lru 算法
	参考:http://flychao88.iteye.com/blog/1977653
- LinkedHashMap
    参考:http://www.cnblogs.com/hubingxu/archive/2012/02/21/2361281.html
	
Lru应该算是很常用的算法了吧,特别是对于缓存这方便来说
Lru缓存,一句话,常用的不会被丢弃,不常用的会被丢弃


```java

public class LruMemoryCache implements MemoryCache {

    /**
     * 缓存存储map
     */
    private final LinkedHashMap<String, Bitmap> map;

    /**
     * 最大缓存值
     */
    private final int maxSize;
    /**
     * 缓存大小,单位字节
     */
    private int size;

    /**
     * @param maxSize Maximum sum of the sizes of the Bitmaps in this cache
     */
    public LruMemoryCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
    }

    /**
     * 获取缓存的bitmap
     * Returns the Bitmap for {@code key} if it exists in the cache. If a Bitmap was returned, it is moved to the head
     * of the queue. This returns null if a Bitmap is not cached.
     */
    @Override
    public final Bitmap get(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        synchronized (this) {
            //LinkHaskMap不是线程安全的
            return map.get(key);
        }
    }

    /**
     * 存入缓存
     * Caches {@code Bitmap} for {@code key}. The Bitmap is moved to the head of the queue.
     */
    @Override
    public final boolean put(String key, Bitmap value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        synchronized (this) {
            size += sizeOf(key, value);
            //移除旧的数据
            Bitmap previous = map.put(key, value);
            if (previous != null) {
                size -= sizeOf(key, previous);
            }
        }

        trimToSize(maxSize);
        return true;
    }

    /**
     * 控制大小的方法
     * Remove the eldest entries until the total of remaining entries is at or below the requested size.
     *
     * @param maxSize the maximum size of the cache before returning. May be -1 to evict even 0-sized elements.
     */
    private void trimToSize(int maxSize) {
        while (true) {
            String key;
            Bitmap value;
            synchronized (this) {
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(getClass().getName() + ".sizeOf() is reporting inconsistent results!");
                }

                if (size <= maxSize || map.isEmpty()) {
                    break;
                }

                Map.Entry<String, Bitmap> toEvict = map.entrySet().iterator().next();
                if (toEvict == null) {
                    break;
                }
                key = toEvict.getKey();
                value = toEvict.getValue();
                map.remove(key);
                size -= sizeOf(key, value);
            }
        }
    }

    /**
     * Removes the entry for {@code key} if it exists.
     */
    @Override
    public final Bitmap remove(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        synchronized (this) {
            Bitmap previous = map.remove(key);
            if (previous != null) {
                size -= sizeOf(key, previous);
            }
            return previous;
        }
    }

    @Override
    public Collection<String> keys() {
        synchronized (this) {
            return new HashSet<String>(map.keySet());
        }
    }

    @Override
    public void clear() {
        trimToSize(-1); // -1 will evict 0-sized elements
    }

    /**
     * Returns the size {@code Bitmap} in bytes.
     * <p/>
     * An entry's size must not change while it is in the cache.
     */
    private int sizeOf(String key, Bitmap value) {
        return value.getRowBytes() * value.getHeight();
    }

    @Override
    public synchronized final String toString() {
        return String.format("LruCache[maxSize=%d]", maxSize);
    }
}

```

注释已经很详细了,其实硬盘缓存也差不多,只是多了文件夹和IO上的一些处理,没有什么本质的区别
	
#### 4 硬盘缓存

在com.nostra13.universalimageloader.cache.disc.impl.ext包下有几个扩展缓存类,有兴趣可以看一下

<p align=center>
<img src="https://ooo.0o0.ooo/2017/06/09/593a467da454e.png"  width=400 />
</p>

#### 5 查询时机

其实在display过程中,有很多次对缓存的查询,下面我一一列出来,就不再粘贴代码了

- ImageLoader.displayImage()方法中第一次内存查找(同步)
- LoadAndDisplayImageTask.run方法第二次内存查找(异步)
- LoadAndDisplayImageTask.tryLoadBitmap方法第一次硬盘查找(异步)
- 网络加载图片(异步)


#### 参考

##### 1 LinkedHashMap

- 顺序排序

//默认是按插入顺序排序，
 // 如果指定按访问顺序排序，那么调用get方法后，会将这次访问的元素移至链表尾部，不断访问可以形成按访问顺序排序的链表。
// 可以重写removeEldestEntry方法返回true值指定插入元素时移除最老的元素。

- 负荷系数

如果负载因子是0.75，hashmap(16)最多可以存储12个元素，想存第16个就得扩容成32。
如果负载因子是1，hashmap(16)最多可以存储16个元素。

同样存16个元素，一个占了32个空间，一个占了16个空间的内存。

实际容量 = 最大容量  * 负载因子，如果最大容量不变的情况下增大负载因子，当然可以增加实际容量，如果负载因子大了会增加哈希冲突发生的概率

