## ImageLoader源码解析(一)

本文章只解析初始化和加载一个图片的整体流程,
对于缓存的实现,线程的调度,涉及到的一些技术(线程池等)
放到以后再解析

### 1 文件目录解析

- cache 缓存
- cache/disc 硬盘缓存
- cache/memory 内存缓存

- core 主要功能实现代码



- core/assit 一些类的封装(失败原因,图片大小,流的刷新,图片采样率等)
- core/assit/deque 加载队列的操作

- core/decode 解码器
- core/display 下载图片之后得到的bitmap的装饰(圆角,淡入淡出等)
- core/download 下载器
- core/imageware 一个对于iamgeview的封装类,包含imageview的一些属性,目前不知道作用是什么
- core/listener 下载监听
- core/process bitmap处理着,接口,没有找到实现类

- utils 工具类(缓存工具,log工具,硬盘工具)


### 2 设计模式
- builder模式
- 工厂模式
- 单利模式


### 3 解析

#### 3.1 初始化

##### 3.1.1 标准的初始化流程

```java
	ImageLoaderConfiguration.Builder config = new ImageLoaderConfiguration.Builder(context);
		config.threadPriority(Thread.NORM_PRIORITY - 2);
		config.denyCacheImageMultipleSizesInMemory();
		config.diskCacheFileNameGenerator(new Md5FileNameGenerator());
		config.diskCacheSize(50 * 1024 * 1024); // 50 MiB
		config.tasksProcessingOrder(QueueProcessingType.LIFO);
		config.writeDebugLogs(); // Remove for release app

		// Initialize ImageLoader with configuration.
		ImageLoader.getInstance().init(config.build());

```
##### 3.1.2 ImageLoaderConfiguration.Builder

ImageLoaderConfiguration是采用Builder构建者模式,咱们先去看Builder里面的代码

```java

public static class Builder {
        //一些异常提示文字
        private static final String WARNING_OVERLAP_DISK_CACHE_PARAMS = "diskCache(), diskCacheSize() and diskCacheFileCount calls overlap each other";
        private static final String WARNING_OVERLAP_DISK_CACHE_NAME_GENERATOR = "diskCache() and diskCacheFileNameGenerator() calls overlap each other";
        private static final String WARNING_OVERLAP_MEMORY_CACHE = "memoryCache() and memoryCacheSize() calls overlap each other";
        private static final String WARNING_OVERLAP_EXECUTOR = "threadPoolSize(), threadPriority() and tasksProcessingOrder() calls "
                + "can overlap taskExecutor() and taskExecutorForCachedImages() calls.";

        /**
         * 默认线程池大小
         */
        public static final int DEFAULT_THREAD_POOL_SIZE = 3;

        /**
         * 默认线程优先级
         */
        public static final int DEFAULT_THREAD_PRIORITY = Thread.NORM_PRIORITY - 2;

        /**
         * 默认队列调度模式{@link QueueProcessingType}
         */
        public static final QueueProcessingType DEFAULT_TASK_PROCESSING_TYPE = QueueProcessingType.FIFO;

        private Context context;

        //图片基本属性
        private int maxImageWidthForMemoryCache = 0;
        private int maxImageHeightForMemoryCache = 0;
        private int maxImageWidthForDiskCache = 0;
        private int maxImageHeightForDiskCache = 0;

        private BitmapProcessor processorForDiskCache = null;

        /**
         * 任务调度者
         */
        private Executor taskExecutor = null;
        /**
         * 缓存任务调度者
         */
        private Executor taskExecutorForCachedImages = null;
        //自定义任务调度者
        private boolean customExecutor = false;
        private boolean customExecutorForCachedImages = false;

        private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;
        private int threadPriority = DEFAULT_THREAD_PRIORITY;
        /**
         * 不缓存多种尺寸?
         */
        private boolean denyCacheImageMultipleSizesInMemory = false;

        /**
         * 任务调度模式
         */
        private QueueProcessingType tasksProcessingType = DEFAULT_TASK_PROCESSING_TYPE;

        private int memoryCacheSize = 0;
        private long diskCacheSize = 0;
        private int diskCacheFileCount = 0;

        //缓存
        private MemoryCache memoryCache = null;
        private DiskCache diskCache = null;
        /**
         * 文件名称生成工具,可以自定义
         * {@link com.nostra13.universalimageloader.cache.disc.naming.HashCodeFileNameGenerator}$<br/>
         * {@link com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator}
         */
        private FileNameGenerator diskCacheFileNameGenerator = null;
        /**
         * 图片下载器
         * {@link com.nostra13.universalimageloader.core.decode.BaseImageDecoder}
         */
        private ImageDownloader downloader = null;
        /**
         * Image解码器
         */
        private ImageDecoder decoder;
        /**
         * 加载图片的配置,针对单个图片,可以覆盖默认配置,如果在这里设置了,应该是将该option作为默认option
         */
        private DisplayImageOptions defaultDisplayImageOptions = null;
        /**
         * 是否打印日志
         */
        private boolean writeLogs = false;
		//下面就是一些set方法
		....
		//创建方法
		 /** Builds configured {@link ImageLoaderConfiguration} object */
        public ImageLoaderConfiguration build() {
            initEmptyFieldsWithDefaultValues();
            return new ImageLoaderConfiguration(this);
        }
		}

```
##### 3.1.3  ImageLoaderConfiguration
 
 构建者模式基本做了许多相应的处理,ImageLoaderConfiguration只负责记录构建者传过来的配置即可
 
 ```java
 
 
		 //成员变量都是final的,也就是不可修改的,更加安全
	  	final Executor taskExecutor;
   		final Executor taskExecutorForCachedImages;
   		final boolean customExecutor;
   		final boolean customExecutorForCachedImages;
 		....
	 /**
     * 从构建者中拿到配置,初始化配置
     * @param builder
     */
    private ImageLoaderConfiguration(final Builder builder) {
	
	
        resources = builder.context.getResources();
        maxImageWidthForMemoryCache = builder.maxImageWidthForMemoryCache;
        maxImageHeightForMemoryCache = builder.maxImageHeightForMemoryCache;
        maxImageWidthForDiskCache = builder.maxImageWidthForDiskCache;
        maxImageHeightForDiskCache = builder.maxImageHeightForDiskCache;
        processorForDiskCache = builder.processorForDiskCache;
        taskExecutor = builder.taskExecutor;
        taskExecutorForCachedImages = builder.taskExecutorForCachedImages;
        threadPoolSize = builder.threadPoolSize;
        threadPriority = builder.threadPriority;
        tasksProcessingType = builder.tasksProcessingType;
        diskCache = builder.diskCache;
        memoryCache = builder.memoryCache;
        defaultDisplayImageOptions = builder.defaultDisplayImageOptions;
        downloader = builder.downloader;
        decoder = builder.decoder;

        customExecutor = builder.customExecutor;
        customExecutorForCachedImages = builder.customExecutorForCachedImages;

        networkDeniedDownloader = new NetworkDeniedImageDownloader(downloader);
        slowNetworkDownloader = new SlowNetworkImageDownloader(downloader);

        L.writeDebugLogs(builder.writeLogs);
    }
 
 ```
这里面有两个类,需要注意一下,ImageLoaderConfiguration.NetworkDeniedImageDownloader和
SlowNetworkImageDownloader,目前还无法确定其具体用途,看名字是网络错误的加载器和网络
太慢的加载器

##### 3.1.4 ImageLoader.getInstance.init

Imageloader代码比较多,先分析初始化的代码

```java
public class ImageLoader {

    public static final String TAG = ImageLoader.class.getSimpleName();

    static final String LOG_INIT_CONFIG = "Initialize ImageLoader with configuration";
    static final String LOG_DESTROY = "Destroy ImageLoader";
    static final String LOG_LOAD_IMAGE_FROM_MEMORY_CACHE = "Load image from memory cache [%s]";

    private static final String WARNING_RE_INIT_CONFIG = "Try to initialize ImageLoader which had already been initialized before. " + "To re-init ImageLoader with new configuration call ImageLoader.destroy() at first.";
    private static final String ERROR_WRONG_ARGUMENTS = "Wrong arguments were passed to displayImage() method (ImageView reference must not be null)";
    private static final String ERROR_NOT_INIT = "ImageLoader must be init with configuration before using";
    private static final String ERROR_INIT_CONFIG_WITH_NULL = "ImageLoader configuration can not be initialized with null";

    private ImageLoaderConfiguration configuration;
    private ImageLoaderEngine engine;

    private ImageLoadingListener defaultListener = new SimpleImageLoadingListener();

    private volatile static ImageLoader instance;

    /** Returns singleton class instance
     * 常用的double check lock单例方法,最常使用的一种方式,在我看来,没有之一
     * */
    public static ImageLoader getInstance() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new ImageLoader();
                }
            }
        }
        return instance;
    }

    protected ImageLoader() {
    }
	/**
     *
     * 初始化方法,做的事情不多,初始化加载引擎,接收configuration
     * Initializes ImageLoader instance with configuration.<br />
     * If configurations was set before ( {@link #isInited()} == true) then this method does nothing.<br />
     * To force initialization with new configuration you should {@linkplain #destroy() destroy ImageLoader} at first.
     *
     * @param configuration {@linkplain ImageLoaderConfiguration ImageLoader configuration}
     * @throws IllegalArgumentException if <b>configuration</b> parameter is null
     */
    public synchronized void init(ImageLoaderConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException(ERROR_INIT_CONFIG_WITH_NULL);
        }
        if (this.configuration == null) {
            engine = new ImageLoaderEngine(configuration);
            this.configuration = configuration;
        } else {
            L.w(WARNING_RE_INIT_CONFIG);
        }
    }
```


#### 3.2 加载过程分析

这里有几个关键类,先看一下这几个类的源码

##### 3.2.1 DisplayImageOptions
该类似定义了本次加载的配置
本类也是采用了构建者模式,这里只粘贴一下有什么属性,不再粘贴所有源码

```java

public final class DisplayImageOptions {
	/**
	 * 加载中图片
	 */
	private final int imageResOnLoading;
	/**
	 * 空URL 图片
	 */
	private final int imageResForEmptyUri;
	/**
	 * 加载失败图片
	 */
	private final int imageResOnFail;
	private final Drawable imageOnLoading;
	private final Drawable imageForEmptyUri;
	private final Drawable imageOnFail;

	/**
	 * 是否重新设置View,在加载之前(不太明白)
	 */
	private final boolean resetViewBeforeLoading;
	/**
	 * 在内存中缓存?
	 */
	private final boolean cacheInMemory;
	/**
	 * 在硬盘中缓存?
	 */
	private final boolean cacheOnDisk;
	/**
	 * 解码时缩放类型
	 */
	private final ImageScaleType imageScaleType;
	/**
	 * 解码配置,SDK_API
	 * @{link android.graphics.BitmapFactory}
	 */
	private final Options decodingOptions;
	private final int delayBeforeLoading;
	private final boolean considerExifParams;
	/**
	 * 下载器额外参数
	 */
	private final Object extraForDownloader;
	
	private final BitmapProcessor preProcessor;
	private final BitmapProcessor postProcessor;
	
	private final BitmapDisplayer displayer;
	private final Handler handler;
	/**
	 * 异步该值始终为false
	 */
	private final boolean isSyncLoading;
}

```
##### 3.2.2 ImageAware

它是个抽象类,主要用于包装ImageView,有两个实现类:
1 ImageViewAware
2 NonViewAware 这个是用于不往ImageView中设置图片的时候使用的,不关注

ImageViewAware继承ViewAware,ViewAware继承ImageAware

- ImageAware.java,简单注释,官方的注释就不要了

```java
public interface ImageAware {
	int getWidth();

	int getHeight();

	/**
	 * 缩放类型
	 */
	ViewScaleType getScaleType();

	/**
	 * 获取的其实就内部的view,也就是咱们传入的ImageView
	 */
	View getWrappedView();

	/**
	 * 是否被回收
	 */
	boolean isCollected();
	//
	int getId();

	boolean setImageDrawable(Drawable drawable);

	boolean setImageBitmap(Bitmap bitmap);
}
```

- ViewAware
   其实ViewAware做的最主要的事情就是将Imagview用弱引用存起来
   
```java
public abstract class ViewAware implements ImageAware {

    /**
     * View的引用,实际使用的是弱引用
     * {@link ViewAware#ViewAware(View view, boolean checkActualViewSize)}
     */
    protected Reference<View> viewRef;
    protected boolean checkActualViewSize;

    /**
     * Constructor. <br />
     * References {@link #ViewAware(android.view.View, boolean) ImageViewAware(imageView, true)}.
     * @param view {@link android.view.View View} to work with
     */
    public ViewAware(View view) {
        this(view, true);
    }

    public ViewAware(View view, boolean checkActualViewSize) {
        if (view == null) throw new IllegalArgumentException("view must not be null");

        this.viewRef = new WeakReference<View>(view);
        this.checkActualViewSize = checkActualViewSize;
    }
	....
}

```
其实看到这里,ImageAware是干什么的就很清楚了

- ImageViewAware

```java
public class ImageViewAware extends ViewAware {

	//构造函数
	....
	/**
	 * {@inheritDoc}
	 * <br />
	 * 3) Get <b>maxWidth</b>.
	 */
	@Override
	public int getWidth() {
		int width = super.getWidth();
		if (width <= 0) {
			ImageView imageView = (ImageView) viewRef.get();
			if (imageView != null) {
				width = getImageViewFieldValue(imageView, "mMaxWidth"); // Check maxWidth parameter
			}
		}
		return width;
	}

	/**
	 * {@inheritDoc}
	 * <br />
	 * 3) Get <b>maxHeight</b>
	 */
	@Override
	public int getHeight() {
		int height = super.getHeight();
		if (height <= 0) {
			ImageView imageView = (ImageView) viewRef.get();
			if (imageView != null) {
				height = getImageViewFieldValue(imageView, "mMaxHeight"); // Check maxHeight parameter
			}
		}
		return height;
	}

	@Override
	public ViewScaleType getScaleType() {
		ImageView imageView = (ImageView) viewRef.get();
		if (imageView != null) {
			return ViewScaleType.fromImageView(imageView);
		}
		return super.getScaleType();
	}

	@Override
	public ImageView getWrappedView() {
		return (ImageView) super.getWrappedView();
	}

	@Override
	protected void setImageDrawableInto(Drawable drawable, View view) {
		((ImageView) view).setImageDrawable(drawable);
		if (drawable instanceof AnimationDrawable) {
			((AnimationDrawable)drawable).start();
		}
	}

	@Override
	protected void setImageBitmapInto(Bitmap bitmap, View view) {
		((ImageView) view).setImageBitmap(bitmap);
	}

	/**
	 * 利用反射获取ImageView的最大宽度和高度
	 * 还真是反射无处不在
	 * @param object
	 * @param fieldName
     * @return
     */
	private static int getImageViewFieldValue(Object object, String fieldName) {
		int value = 0;
		try {
			Field field = ImageView.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			int fieldValue = (Integer) field.get(object);
			if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
				value = fieldValue;
			}
		} catch (Exception e) {
			L.e(e);
		}
		return value;
	}
}

```
##### 3.2.3 ImageLoadingListener&ImageLoadingProgressListener略


##### 3.2.4 ImageLoader.display 加载入口方法

其实dispay方法最终只会走向一个display方法,也就是下面这个方法
```java

 public void displayImage(String uri, ImageAware imageAware, DisplayImageOptions options,
                             ImageSize targetSize, ImageLoadingListener listener,
                             ImageLoadingProgressListener progressListener) {
        //检查参数
        checkConfiguration();
        if (imageAware == null) {
            throw new IllegalArgumentException(ERROR_WRONG_ARGUMENTS);
        }
        if (listener == null) {
            listener = defaultListener;
        }
        if (options == null) {
            //如果加载配置为空,那么使用默认加载配置
            options = configuration.defaultDisplayImageOptions;
        }

        if (TextUtils.isEmpty(uri)) {
            engine.cancelDisplayTaskFor(imageAware);
            listener.onLoadingStarted(uri, imageAware.getWrappedView());
            if (options.shouldShowImageForEmptyUri()) {
                imageAware.setImageDrawable(options.getImageForEmptyUri(configuration.resources));
            } else {
                imageAware.setImageDrawable(null);
            }
            listener.onLoadingComplete(uri, imageAware.getWrappedView(), null);
            return;
        }

        if (targetSize == null) {
            targetSize = ImageSizeUtils.defineTargetSizeForView(imageAware, configuration.getMaxImageSize());
        }
//      获取文件内存缓存key
        String memoryCacheKey = MemoryCacheUtils.generateKey(uri, targetSize);
        //准备开始请求
        engine.prepareDisplayTaskFor(imageAware, memoryCacheKey);
        //监听方法
        listener.onLoadingStarted(uri, imageAware.getWrappedView());

        //先尝试从缓存拿
        Bitmap bmp = configuration.memoryCache.get(memoryCacheKey);
        if (bmp != null && !bmp.isRecycled()) {
            //从内存中找到并且没有被回收
            L.d(LOG_LOAD_IMAGE_FROM_MEMORY_CACHE, memoryCacheKey);

            if (options.shouldPostProcess()) {
                //需要转换? 默认是不需要转换的
                //构建加载信息对象
                ImageLoadingInfo imageLoadingInfo = new ImageLoadingInfo(uri, imageAware, targetSize, memoryCacheKey,
                        options, listener, progressListener, engine.getLockForUri(uri));
                ProcessAndDisplayImageTask displayTask = new ProcessAndDisplayImageTask(engine, bmp, imageLoadingInfo,
                        defineHandler(options));
                if (options.isSyncLoading()) {
                    //同步执行
                    displayTask.run();
                } else {
                    engine.submit(displayTask);
                }
            } else {
                //直接加载
                options.getDisplayer().display(bmp, imageAware, LoadedFrom.MEMORY_CACHE);
                listener.onLoadingComplete(uri, imageAware.getWrappedView(), bmp);
            }
        } else {
            if (options.shouldShowImageOnLoading()) {
                //显示加载中的图片
                imageAware.setImageDrawable(options.getImageOnLoading(configuration.resources));
            } else if (options.isResetViewBeforeLoading()) {
                //加载前重置View,其实就是清空原有图片
                imageAware.setImageDrawable(null);
            }

            ImageLoadingInfo imageLoadingInfo = new ImageLoadingInfo(uri, imageAware, targetSize, memoryCacheKey,
                    options, listener, progressListener, engine.getLockForUri(uri));
            LoadAndDisplayImageTask displayTask = new LoadAndDisplayImageTask(engine, imageLoadingInfo,
                    defineHandler(options));
            if (options.isSyncLoading()) {
                //同步
                displayTask.run();
            } else {
                //异步
                engine.submit(displayTask);
            }
        }
    }

```

其实上面的逻辑很清楚,咱们先关注主要流程,上面的代码其实就是,先从内存中加载图片,
然后再进行一系列的判断,如果内存中有,直接加载


如果内存中没有,并且是异步加载,那么一定会走engine.submit(displayTask);这个方法,咱们追进去看看
ImageLoadingInfo,LoadAndDisplayImageTask这两个类,一会再讲
- ImageLoaderEngine.java
```java
class ImageLoaderEngine {

    final ImageLoaderConfiguration configuration;

    //两个线程池
    /**
     * 网络加载线程池
     */
    private Executor taskExecutor;
    /**
     * 硬盘缓存线程池
     */
    private Executor taskExecutorForCachedImages;
    /**
     * 默认线程执行着,其实就是ThreadPoolExecutor
     */
    private Executor taskDistributor;
	
	 /**
     * 将任务提交到线程池
     * Submits task to execution pool
     */
    void submit(final LoadAndDisplayImageTask task) {
        taskDistributor.execute(new Runnable() {
            //线程池执行
            @Override
            public void run() {
                //先从硬盘中获取硬盘缓存
                File image = configuration.diskCache.get(task.getLoadingUri());
                boolean isImageCachedOnDisk = image != null && image.exists();
                initExecutorsIfNeed();
                if (isImageCachedOnDisk) {
                    //从缓存
                    taskExecutorForCachedImages.execute(task);
                } else {
                    taskExecutor.execute(task);
                }
            }
        });
    }
	
	.....
	//其他方法省略
}

```
Executor不了解,请百度,或者看文后参考
上面方法很简单,从硬盘缓存中获取文件,这里有两个线程池,一个是专门从硬盘拿数据的,一个是专门从网络拿数据的,线程的调度先不考虑
其实task就是一个实现了Runable接口的类,接下来的分析转入到 到Task中,执行的一定是task.run()方法


##### 3.2.5 加载任务解析

###### 3.2.5.1 ImageLoadingInfo

在解析这个类之前,先了解另外一个类ImageLoadingInfo

- ImageLoadingInfo 本次加载信息实体类,没什么好说的

```java

/**
 *
 * 加载的图片信息实体类,看属性
 * Information for load'n'display image task
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @see com.nostra13.universalimageloader.utils.MemoryCacheUtils
 * @see DisplayImageOptions
 * @see ImageLoadingListener
 * @see com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener
 * @since 1.3.1
 */
final class ImageLoadingInfo {

    final String uri;
    final String memoryCacheKey;
    final ImageAware imageAware;
    final ImageSize targetSize;
    final DisplayImageOptions options;
    final ImageLoadingListener listener;
    final ImageLoadingProgressListener progressListener;
    /**
     * 该锁的理解请百度,文后也有连接<br/>
     * 猜测:有多个任务对同一个View处理的时候,需要用到吧
     */
    final ReentrantLock loadFromUriLock;

    public ImageLoadingInfo(String uri, ImageAware imageAware, ImageSize targetSize, String memoryCacheKey,
                            DisplayImageOptions options, ImageLoadingListener listener,
                            ImageLoadingProgressListener progressListener, ReentrantLock loadFromUriLock) {
        this.uri = uri;
        this.imageAware = imageAware;
        this.targetSize = targetSize;
        this.options = options;
        this.listener = listener;
        this.progressListener = progressListener;
        this.loadFromUriLock = loadFromUriLock;
        this.memoryCacheKey = memoryCacheKey;
    }
}
```
###### 3.2.5.2 LoadAndDisplayImageTask

```java

final class LoadAndDisplayImageTask implements Runnable, IoUtils.CopyListener {

    private final ImageLoaderEngine engine;
    private final ImageLoadingInfo imageLoadingInfo;
    private final Handler handler;

    // Helper references
    private final ImageLoaderConfiguration configuration;
    private final ImageDownloader downloader;
    private final ImageDownloader networkDeniedDownloader;
    private final ImageDownloader slowNetworkDownloader;
    private final ImageDecoder decoder;
    final String uri;
    private final String memoryCacheKey;
    final ImageAware imageAware;
    private final ImageSize targetSize;
    final DisplayImageOptions options;
    final ImageLoadingListener listener;
    final ImageLoadingProgressListener progressListener;
    private final boolean syncLoading;

    // State vars
    private LoadedFrom loadedFrom = LoadedFrom.NETWORK;

    public LoadAndDisplayImageTask(ImageLoaderEngine engine, ImageLoadingInfo imageLoadingInfo, Handler handler) {
        this.engine = engine;
        this.imageLoadingInfo = imageLoadingInfo;
        this.handler = handler;

        configuration = engine.configuration;
        downloader = configuration.downloader;
        networkDeniedDownloader = configuration.networkDeniedDownloader;
        slowNetworkDownloader = configuration.slowNetworkDownloader;
        decoder = configuration.decoder;
        uri = imageLoadingInfo.uri;
        memoryCacheKey = imageLoadingInfo.memoryCacheKey;
        imageAware = imageLoadingInfo.imageAware;
        targetSize = imageLoadingInfo.targetSize;
        options = imageLoadingInfo.options;
        listener = imageLoadingInfo.listener;
        progressListener = imageLoadingInfo.progressListener;
        syncLoading = options.isSyncLoading();
    }

    @Override
    public void run() {
        if (waitIfPaused()) return;
        if (delayIfNeed()) return;

        ReentrantLock loadFromUriLock = imageLoadingInfo.loadFromUriLock;
        L.d(LOG_START_DISPLAY_IMAGE_TASK, memoryCacheKey);
        if (loadFromUriLock.isLocked()) {
            //已经在加载,
            L.d(LOG_WAITING_FOR_IMAGE_LOADED, memoryCacheKey);
        }
        //加锁
        loadFromUriLock.lock();
        Bitmap bmp;
        try {
            //检查View
            checkTaskNotActual();
            //内存缓存
            bmp = configuration.memoryCache.get(memoryCacheKey);
            if (bmp == null || bmp.isRecycled()) {
                //没有内存
                bmp = tryLoadBitmap();
                if (bmp == null) return; // listener callback already was fired

                checkTaskNotActual();
                checkTaskInterrupted();

                if (options.shouldPreProcess()) {
                    L.d(LOG_PREPROCESS_IMAGE, memoryCacheKey);
                    bmp = options.getPreProcessor().process(bmp);
                    if (bmp == null) {
                        L.e(ERROR_PRE_PROCESSOR_NULL, memoryCacheKey);
                    }
                }

                if (bmp != null && options.isCacheInMemory()) {
                    //放入内存
                    L.d(LOG_CACHE_IMAGE_IN_MEMORY, memoryCacheKey);
                    configuration.memoryCache.put(memoryCacheKey, bmp);
                }
            } else {
                loadedFrom = LoadedFrom.MEMORY_CACHE;
                L.d(LOG_GET_IMAGE_FROM_MEMORY_CACHE_AFTER_WAITING, memoryCacheKey);
            }

            if (bmp != null && options.shouldPostProcess()) {
                L.d(LOG_POSTPROCESS_IMAGE, memoryCacheKey);
                bmp = options.getPostProcessor().process(bmp);
                if (bmp == null) {
                    L.e(ERROR_POST_PROCESSOR_NULL, memoryCacheKey);
                }
            }
            checkTaskNotActual();
            checkTaskInterrupted();
        } catch (TaskCancelledException e) {
            //取消加载
            fireCancelEvent();
            return;
        } finally {
            //解锁
            loadFromUriLock.unlock();
        }

        DisplayBitmapTask displayBitmapTask = new DisplayBitmapTask(bmp, imageLoadingInfo, engine, loadedFrom);
        runTask(displayBitmapTask, syncLoading, handler, engine);
    }

    /**
     * 任务是否暂停
     * @return <b>true</b> - if task should be interrupted; <b>false</b> - otherwise
     */
    private boolean waitIfPaused() {
        AtomicBoolean pause = engine.getPause();
        if (pause.get()) {
            synchronized (engine.getPauseLock()) {
                if (pause.get()) {
                    L.d(LOG_WAITING_FOR_RESUME, memoryCacheKey);
                    try {
                        engine.getPauseLock().wait();
                    } catch (InterruptedException e) {
                        L.e(LOG_TASK_INTERRUPTED, memoryCacheKey);
                        return true;
                    }
                    L.d(LOG_RESUME_AFTER_PAUSE, memoryCacheKey);
                }
            }
        }
        return isTaskNotActual();
    }

    /**
     * 延迟时间
     * @return <b>true</b> - if task should be interrupted; <b>false</b> - otherwise
     */
    private boolean delayIfNeed() {
        if (options.shouldDelayBeforeLoading()) {
            L.d(LOG_DELAY_BEFORE_LOADING, options.getDelayBeforeLoading(), memoryCacheKey);
            try {
                Thread.sleep(options.getDelayBeforeLoading());
            } catch (InterruptedException e) {
                L.e(LOG_TASK_INTERRUPTED, memoryCacheKey);
                return true;
            }
            return isTaskNotActual();
        }
        return false;
    }

    /**
     * 加载图片数据(硬盘&网络)
     * @return
     * @throws TaskCancelledException
     */
    private Bitmap tryLoadBitmap() throws TaskCancelledException {
        Bitmap bitmap = null;
        try {
            //硬盘加载
            File imageFile = configuration.diskCache.get(uri);
            if (imageFile != null && imageFile.exists() && imageFile.length() > 0) {
                L.d(LOG_LOAD_IMAGE_FROM_DISK_CACHE, memoryCacheKey);
                loadedFrom = LoadedFrom.DISC_CACHE;

                checkTaskNotActual();
                bitmap = decodeImage(Scheme.FILE.wrap(imageFile.getAbsolutePath()));
            }
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                L.d(LOG_LOAD_IMAGE_FROM_NETWORK, memoryCacheKey);
                loadedFrom = LoadedFrom.NETWORK;

                String imageUriForDecoding = uri;
                if (options.isCacheOnDisk() && tryCacheImageOnDisk()) {
                    imageFile = configuration.diskCache.get(uri);
                    if (imageFile != null) {
                        imageUriForDecoding = Scheme.FILE.wrap(imageFile.getAbsolutePath());
                    }
                }

                checkTaskNotActual();
				//真正加载网络图片的方法
                bitmap = decodeImage(imageUriForDecoding);

                if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                    fireFailEvent(FailType.DECODING_ERROR, null);
                }
            }
        } catch (IllegalStateException e) {
            fireFailEvent(FailType.NETWORK_DENIED, null);
        } catch (TaskCancelledException e) {
            throw e;
        } catch (IOException e) {
            L.e(e);
            fireFailEvent(FailType.IO_ERROR, e);
        } catch (OutOfMemoryError e) {
            L.e(e);
            fireFailEvent(FailType.OUT_OF_MEMORY, e);
        } catch (Throwable e) {
            L.e(e);
            fireFailEvent(FailType.UNKNOWN, e);
        }
        return bitmap;
    }

    /**
     * 从网络加载图片
     * @param imageUri
     * @return
     * @throws IOException
     */
    private Bitmap decodeImage(String imageUri) throws IOException {
        ViewScaleType viewScaleType = imageAware.getScaleType();
        ImageDecodingInfo decodingInfo = new ImageDecodingInfo(memoryCacheKey, imageUri, uri, targetSize, viewScaleType,
                getDownloader(), options);
        return decoder.decode(decodingInfo);
    }

    /**
     * 加载成功,缓存文件放入本地磁盘
     * @return <b>true</b> - if image was downloaded successfully; <b>false</b> - otherwise
     */
    private boolean tryCacheImageOnDisk() throws TaskCancelledException {
        L.d(LOG_CACHE_IMAGE_ON_DISK, memoryCacheKey);

        boolean loaded;
        try {
            loaded = downloadImage();
            if (loaded) {
                int width = configuration.maxImageWidthForDiskCache;
                int height = configuration.maxImageHeightForDiskCache;
                if (width > 0 || height > 0) {
                    L.d(LOG_RESIZE_CACHED_IMAGE_FILE, memoryCacheKey);
                    resizeAndSaveImage(width, height); // TODO : process boolean result
                }
            }
        } catch (IOException e) {
            L.e(e);
            loaded = false;
        }
        return loaded;
    }

    /**
     * 下载文件到磁盘
     * @return
     * @throws IOException
     */
    private boolean downloadImage() throws IOException {
        InputStream is = getDownloader().getStream(uri, options.getExtraForDownloader());
        if (is == null) {
            L.e(ERROR_NO_IMAGE_STREAM, memoryCacheKey);
            return false;
        } else {
            try {
                return configuration.diskCache.save(uri, is, this);
            } finally {
                IoUtils.closeSilently(is);
            }
        }
    }

    /**
     * 重新设置image大小,并且保存
     * Decodes image file into Bitmap, resize it and save it back
     */
    private boolean resizeAndSaveImage(int maxWidth, int maxHeight) throws IOException {
        // Decode image file, compress and re-save it
        boolean saved = false;
        File targetFile = configuration.diskCache.get(uri);
        if (targetFile != null && targetFile.exists()) {
            ImageSize targetImageSize = new ImageSize(maxWidth, maxHeight);
            DisplayImageOptions specialOptions = new DisplayImageOptions.Builder().cloneFrom(options)
                    .imageScaleType(ImageScaleType.IN_SAMPLE_INT).build();
            ImageDecodingInfo decodingInfo = new ImageDecodingInfo(memoryCacheKey,
                    Scheme.FILE.wrap(targetFile.getAbsolutePath()), uri, targetImageSize, ViewScaleType.FIT_INSIDE,
                    getDownloader(), specialOptions);
            Bitmap bmp = decoder.decode(decodingInfo);
            if (bmp != null && configuration.processorForDiskCache != null) {
                L.d(LOG_PROCESS_IMAGE_BEFORE_CACHE_ON_DISK, memoryCacheKey);
                bmp = configuration.processorForDiskCache.process(bmp);
                if (bmp == null) {
                    L.e(ERROR_PROCESSOR_FOR_DISK_CACHE_NULL, memoryCacheKey);
                }
            }
            if (bmp != null) {
                saved = configuration.diskCache.save(uri, bmp);
                bmp.recycle();
            }
        }
        return saved;
    }

    @Override
    public boolean onBytesCopied(int current, int total) {
        return syncLoading || fireProgressEvent(current, total);
    }

    /**
     * 处理进度事件
     * @return <b>true</b> - if loading should be continued; <b>false</b> - if loading should be interrupted
     */
    private boolean fireProgressEvent(final int current, final int total) {
        if (isTaskInterrupted() || isTaskNotActual()) return false;
        if (progressListener != null) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    progressListener.onProgressUpdate(uri, imageAware.getWrappedView(), current, total);
                }
            };
            runTask(r, false, handler, engine);
        }
        return true;
    }

    /**
     * 处理失败的事件
     * @param failType
     * @param failCause
     */
    private void fireFailEvent(final FailType failType, final Throwable failCause) {
        if (syncLoading || isTaskInterrupted() || isTaskNotActual()) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (options.shouldShowImageOnFail()) {
                    imageAware.setImageDrawable(options.getImageOnFail(configuration.resources));
                }
                listener.onLoadingFailed(uri, imageAware.getWrappedView(), new FailReason(failType, failCause));
            }
        };
        runTask(r, false, handler, engine);
    }

    /**
     * 取消
     */
    private void fireCancelEvent() {
        if (syncLoading || isTaskInterrupted()) return;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                //监听回调
                listener.onLoadingCancelled(uri, imageAware.getWrappedView());
            }
        };
        runTask(r, false, handler, engine);
    }

    /**
     * 根据网络环境获取不同的下载器
     * @return
     */
    private ImageDownloader getDownloader() {
        ImageDownloader d;
        if (engine.isNetworkDenied()) {
            d = networkDeniedDownloader;
        } else if (engine.isSlowNetwork()) {
            d = slowNetworkDownloader;
        } else {
            d = downloader;
        }
        return d;
    }

    /**
     * 检查View是否可用
     * @throws TaskCancelledException if task is not actual (target ImageAware is collected by GC or the image URI of
     *                                this task doesn't match to image URI which is actual for current ImageAware at
     *                                this moment)
     */
    private void checkTaskNotActual() throws TaskCancelledException {
        checkViewCollected();
        checkViewReused();
    }

    /**
     * @return <b>true</b> - if task is not actual (target ImageAware is collected by GC or the image URI of this task
     * doesn't match to image URI which is actual for current ImageAware at this moment)); <b>false</b> - otherwise
     */
    private boolean isTaskNotActual() {
        return isViewCollected() || isViewReused();
    }

    /**
     * 检查view被回收
     *
     * @throws TaskCancelledException if target ImageAware is collected
     */
    private void checkViewCollected() throws TaskCancelledException {
        if (isViewCollected()) {
            throw new TaskCancelledException();
        }
    }

    /**
     * view是否被回收
     * @return <b>true</b> - if target ImageAware is collected by GC; <b>false</b> - otherwise
     */
    private boolean isViewCollected() {
        if (imageAware.isCollected()) {
            L.d(LOG_TASK_CANCELLED_IMAGEAWARE_COLLECTED, memoryCacheKey);
            return true;
        }
        return false;
    }

    /**
     * @throws TaskCancelledException if target ImageAware is collected by GC
     */
    private void checkViewReused() throws TaskCancelledException {
        if (isViewReused()) {
            throw new TaskCancelledException();
        }
    }

    /**
     * 该View是否已经 加载了另一个图片
     *
     * @return <b>true</b> - if current ImageAware is reused for displaying another image; <b>false</b> - otherwise
     */
    private boolean isViewReused() {
        String currentCacheKey = engine.getLoadingUriForView(imageAware);
        // Check whether memory cache key (image URI) for current ImageAware is actual.
        // If ImageAware is reused for another task then current task should be cancelled.
        boolean imageAwareWasReused = !memoryCacheKey.equals(currentCacheKey);
        if (imageAwareWasReused) {
            L.d(LOG_TASK_CANCELLED_IMAGEAWARE_REUSED, memoryCacheKey);
            return true;
        }
        return false;
    }

    /**
     * 加载是否被终止
     * @throws TaskCancelledException if current task was interrupted
     */
    private void checkTaskInterrupted() throws TaskCancelledException {
        if (isTaskInterrupted()) {
            throw new TaskCancelledException();
        }
    }

    /**
     * 任务被暂停?
     * @return <b>true</b> - if current task was interrupted; <b>false</b> - otherwise
     */
    private boolean isTaskInterrupted() {
        if (Thread.interrupted()) {
            L.d(LOG_TASK_INTERRUPTED, memoryCacheKey);
            return true;
        }
        return false;
    }

    String getLoadingUri() {
        return uri;
    }

    static void runTask(Runnable r, boolean sync, Handler handler, ImageLoaderEngine engine) {
        if (sync) {
            //同步
            r.run();
        } else if (handler == null) {
            engine.fireCallback(r);
        } else {
            handler.post(r);
        }
    }

    /**
     * Exceptions for case when task is cancelled (thread is interrupted, image view is reused for another task, view is
     * collected by GC).
     *
     * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
     * @since 1.9.1
     */
    class TaskCancelledException extends Exception {
    }
}

```
如上可以看到,改类似一个实现了Runnable的类,可以被Executor执行

run方法里面有行代码
`bmp = tryLoadBitmap();`
进入到这个方法,会找到一行代码

```java
 //真正加载网络图片的方法
 bitmap = decodeImage(imageUriForDecoding);
```
再跟进去
就会发现,是调用ImageDecoder去下载图片的,其实这个里面就是调用了ImageDownloader
进行下载

###### 3.2.5.3 ImageDecoder & BaseImageDecoder

它是一个接口,只有一个方法

```java
public interface ImageDecoder {

    /**
     * Decodes image to {@link Bitmap} according target size and other parameters.
     *
     * @param imageDecodingInfo
     * @return
     * @throws IOException
     */
    Bitmap decode(ImageDecodingInfo imageDecodingInfo) throws IOException;
}
```

它真正的逻辑操作,是放在了BaseImageDecoder中.

```java
public class BaseImageDecoder implements ImageDecoder {

    protected final boolean loggingEnabled;

    public BaseImageDecoder(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    /**
     * 从URL中获取Bitmap对象,并根据加载的配置处理图想,主要是大小和scalType采样率的处理
     */
    @Override
    public Bitmap decode(ImageDecodingInfo decodingInfo) throws IOException {
        Bitmap decodedBitmap;
        ImageFileInfo imageInfo;
        //获取输入流
        InputStream imageStream = getImageStream(decodingInfo);
        if (imageStream == null) {
            L.e(ERROR_NO_IMAGE_STREAM, decodingInfo.getImageKey());
            return null;
        }
        try {
            imageInfo = defineImageSizeAndRotation(imageStream, decodingInfo);
            imageStream = resetStream(imageStream, decodingInfo);
            Options decodingOptions = prepareDecodingOptions(imageInfo.imageSize, decodingInfo);
            decodedBitmap = BitmapFactory.decodeStream(imageStream, null, decodingOptions);
        } finally {
            IoUtils.closeSilently(imageStream);
        }

        if (decodedBitmap == null) {
            L.e(ERROR_CANT_DECODE_IMAGE, decodingInfo.getImageKey());
        } else {
            decodedBitmap = considerExactScaleAndOrientatiton(decodedBitmap, decodingInfo, imageInfo.exif.rotation,
                    imageInfo.exif.flipHorizontal);
        }
        return decodedBitmap;
    }

    /**
     * 调用ImageDownloader获取输入流
     * @param decodingInfo
     * @return
     * @throws IOException
     */
    protected InputStream getImageStream(ImageDecodingInfo decodingInfo) throws IOException {
        return decodingInfo.getDownloader().getStream(decodingInfo.getImageUri(), decodingInfo.getExtraForDownloader());
    }

    /**
     * 获取下载文件的信息(大小和旋转)
     * @param imageStream
     * @param decodingInfo
     * @return
     * @throws IOException
     */
    protected ImageFileInfo defineImageSizeAndRotation(InputStream imageStream, ImageDecodingInfo decodingInfo)
            throws IOException {
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(imageStream, null, options);

        ExifInfo exif;
        String imageUri = decodingInfo.getImageUri();
        if (decodingInfo.shouldConsiderExifParams() && canDefineExifParams(imageUri, options.outMimeType)) {
            exif = defineExifOrientation(imageUri);
        } else {
            exif = new ExifInfo();
        }
        return new ImageFileInfo(new ImageSize(options.outWidth, options.outHeight, exif.rotation), exif);
    }

    private boolean canDefineExifParams(String imageUri, String mimeType) {
        return "image/jpeg".equalsIgnoreCase(mimeType) && (Scheme.ofUri(imageUri) == Scheme.FILE);
    }

    /**
     *  旋转角度信息?
     * @param imageUri
     * @return
     */
    protected ExifInfo defineExifOrientation(String imageUri) {
        int rotation = 0;
        boolean flip = false;
        try {
            ExifInterface exif = new ExifInterface(Scheme.FILE.crop(imageUri));
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    flip = true;
                case ExifInterface.ORIENTATION_NORMAL:
                    rotation = 0;
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    flip = true;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    flip = true;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    flip = true;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
            }
        } catch (IOException e) {
            L.w("Can't read EXIF tags from file [%s]", imageUri);
        }
        return new ExifInfo(rotation, flip);
    }

    /**
     * 大小处理
     * @param imageSize
     * @param decodingInfo
     * @return
     */
    protected Options prepareDecodingOptions(ImageSize imageSize, ImageDecodingInfo decodingInfo) {
        ImageScaleType scaleType = decodingInfo.getImageScaleType();
        int scale;
        if (scaleType == ImageScaleType.NONE) {
            scale = 1;
        } else if (scaleType == ImageScaleType.NONE_SAFE) {
            scale = ImageSizeUtils.computeMinImageSampleSize(imageSize);
        } else {
            ImageSize targetSize = decodingInfo.getTargetSize();
            boolean powerOf2 = scaleType == ImageScaleType.IN_SAMPLE_POWER_OF_2;
            scale = ImageSizeUtils.computeImageSampleSize(imageSize, targetSize, decodingInfo.getViewScaleType(), powerOf2);
        }
        if (scale > 1 && loggingEnabled) {
            L.d(LOG_SUBSAMPLE_IMAGE, imageSize, imageSize.scaleDown(scale), scale, decodingInfo.getImageKey());
        }

        Options decodingOptions = decodingInfo.getDecodingOptions();
        decodingOptions.inSampleSize = scale;
        return decodingOptions;
    }

    /**
     * 重置输入流,从头开始读
     * @param imageStream
     * @param decodingInfo
     * @return
     * @throws IOException
     */
    protected InputStream resetStream(InputStream imageStream, ImageDecodingInfo decodingInfo) throws IOException {
        if (imageStream.markSupported()) {
            //若支持mark,重新定位输入流
            try {
                imageStream.reset();
                return imageStream;
            } catch (IOException ignored) {
            }
        }
        //如果不支持,关闭此流
        IoUtils.closeSilently(imageStream);
        //重新获取输入流
        return getImageStream(decodingInfo);
    }

    /**
     * 根据采样方式进行图片采样
     * @param subsampledBitmap
     * @param decodingInfo
     * @param rotation
     * @param flipHorizontal
     * @return
     */
    protected Bitmap considerExactScaleAndOrientatiton(Bitmap subsampledBitmap, ImageDecodingInfo decodingInfo,
                                                       int rotation, boolean flipHorizontal) {
        Matrix m = new Matrix();
        // Scale to exact size if need
        ImageScaleType scaleType = decodingInfo.getImageScaleType();
        if (scaleType == ImageScaleType.EXACTLY || scaleType == ImageScaleType.EXACTLY_STRETCHED) {
            ImageSize srcSize = new ImageSize(subsampledBitmap.getWidth(), subsampledBitmap.getHeight(), rotation);
            float scale = ImageSizeUtils.computeImageScale(srcSize, decodingInfo.getTargetSize(), decodingInfo
                    .getViewScaleType(), scaleType == ImageScaleType.EXACTLY_STRETCHED);
            if (Float.compare(scale, 1f) != 0) {
                m.setScale(scale, scale);

                if (loggingEnabled) {
                    L.d(LOG_SCALE_IMAGE, srcSize, srcSize.scale(scale), scale, decodingInfo.getImageKey());
                }
            }
        }
        // Flip bitmap if need
        if (flipHorizontal) {
            m.postScale(-1, 1);

            if (loggingEnabled) L.d(LOG_FLIP_IMAGE, decodingInfo.getImageKey());
        }
        // Rotate bitmap if need
        if (rotation != 0) {
            m.postRotate(rotation);

            if (loggingEnabled) L.d(LOG_ROTATE_IMAGE, rotation, decodingInfo.getImageKey());
        }

        Bitmap finalBitmap = Bitmap.createBitmap(subsampledBitmap, 0, 0, subsampledBitmap.getWidth(), subsampledBitmap
                .getHeight(), m, true);
        if (finalBitmap != subsampledBitmap) {
            subsampledBitmap.recycle();
        }
        return finalBitmap;
    }

    /**
     * 旋转和翻转信息
     */
    protected static class ExifInfo {

        /**
         * 旋转角度
         */
        public final int rotation;
        /**
         * 水平翻转
         */
        public final boolean flipHorizontal;

        protected ExifInfo() {
            this.rotation = 0;
            this.flipHorizontal = false;
        }

        protected ExifInfo(int rotation, boolean flipHorizontal) {
            this.rotation = rotation;
            this.flipHorizontal = flipHorizontal;
        }
    }

    /**
     * 图想大小和旋转信息
     */
    protected static class ImageFileInfo {

        public final ImageSize imageSize;
        public final ExifInfo exif;

        protected ImageFileInfo(ImageSize imageSize, ExifInfo exif) {
            this.imageSize = imageSize;
            this.exif = exif;
        }
    }
}

```
相关注释已经很详细了,
本类就是从下载器ImageDownloader 总获取输入流,进行一系列的图想处理操作
然后返回处理后的bitmap(decode方法)

到这里,已经拿到了获取到的bitmap数据了,并且已经处理好了,,那么我们又是在那儿将bitmap设置
到imagview中去的呢?

在LoadAndDisplayImageTask.run方法中,最后两行

```java
  DisplayBitmapTask displayBitmapTask = new DisplayBitmapTask(bmp, imageLoadingInfo, engine, loadedFrom);
        runTask(displayBitmapTask, syncLoading, handler, engine);
```
又是一个实现了rannable的类
看它的run方法
DisplayBitmapTask.run()
```java
 if (imageAware.isCollected()) {
            //加载取消
            L.d(LOG_TASK_CANCELLED_IMAGEAWARE_COLLECTED, memoryCacheKey);
            listener.onLoadingCancelled(imageUri, imageAware.getWrappedView());
        } else if (isViewWasReused()) {
            //加载取消
            L.d(LOG_TASK_CANCELLED_IMAGEAWARE_REUSED, memoryCacheKey);
            listener.onLoadingCancelled(imageUri, imageAware.getWrappedView());
        } else {
            //加载成功
            L.d(LOG_DISPLAY_IMAGE_IN_IMAGEAWARE, loadedFrom, memoryCacheKey);
            //真正将图片部署到imageview中的方法
            displayer.display(bitmap, imageAware, loadedFrom);
            engine.cancelDisplayTaskFor(imageAware);
            listener.onLoadingComplete(imageUri, imageAware.getWrappedView(), bitmap);
        }
```
关键代码

`displayer.display(bitmap, imageAware, loadedFrom);`

它其实又是一个接口,这个是不是有点熟悉,比如圆角设置就是通过这个方法实现的
给option设置一个自定义的displayer

找个最简单的看一下里面怎么写的
SimpleBitmapDisplayer.java
```java
public final class SimpleBitmapDisplayer implements BitmapDisplayer {
	@Override
	public void display(Bitmap bitmap, ImageAware imageAware, LoadedFrom loadedFrom) {
		imageAware.setImageBitmap(bitmap);
	}
}
```
ImageAware其实就是ImageViewAware,但是中间还有一个ViewAware,
setImageBitmap就是在ViewAware中被实现的

```java
  @Override
    public boolean setImageBitmap(Bitmap bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            //检查是否在主线程
            View view = viewRef.get();
            if (view != null) {
                setImageBitmapInto(bitmap, view);
                return true;
            }
        } else {
            L.w(WARN_CANT_SET_BITMAP);
        }
        return false;
    }
```
setImageBitmapInto在ImageViewAware中被实现
```java
@Override
	protected void setImageBitmapInto(Bitmap bitmap, View view) {
		((ImageView) view).setImageBitmap(bitmap);
	}
```

现在一个image的加载过程我们就全部清楚了



#### 4 总结

##### 4.1 初始化流程

1 利用构建者模式构建ImageLoaderConfiguration,并提供一些默认参数
2 在Imageloader.init()方法中使用doble check lock 单例模式,实现Imageloader对象的单例化

##### 4.2 加载一张图片的流程

1 构建者构建DisplayImageOptions,传入加载本次图片的配置(是否缓存,display等)
2 在display中进行检查图片是否存在于内存缓存
3 如果没有在内存中,则交给LoadAndDisplayImageTask处理
4 使用ImageDecoder处理图片
5 使用ImageDownloader下载图片
6 LoadAndDisplayImageTask拿到bitmap后再交给DisplayBitmapTask处理
7 DisplayBitmapTask调用BitmapDisplayer加载图片
8 BitmapDisplayer中可以对bitmap进行圆角处理之类的

##### 4.3 体悟

观看源码发现,下载器,display,解码器一直都是引用的接口,并不是一个具体的实现类,
并且在构建者里面提供默认的实现,并且可以自定义实现,自由实现相关接口即可,增加了框架的灵活性. 比如下载器改为okhttp等等

#### 参考

##### 1 ReentrantLock 锁
http://blog.csdn.net/fw0124/article/details/6672522
http://tenyears.iteye.com/blog/48750
http://blog.csdn.net/vernonzheng/article/details/8288251

	
##### 2 Executor&ExecutorService$AbstractExecutorService&ThreadPoolExecutor
  http://www.iteye.com/topic/366591/
  
##### 3 BlockingQueue

http://wsmajunfeng.iteye.com/blog/1629354

http://blog.csdn.net/chenchaofuck1/article/details/51657458