## ImageLoader源码解析(四) 补充 针对ListView或者RecycleView的优化


### 1 前言

前几篇,我忽略了一个类,这个类在com.nostra13.universalimageloader.core.listener包下,它所做的事情是,针对ListView,GridView,RecycleView做优化,其实很简单,就是在滑动的时候暂停加载,在滑动结束后重新启动加载,我们来看下这个类

### 2 PauseOnScrollListener


```java
public class PauseOnScrollListener implements OnScrollListener {

	private ImageLoader imageLoader;

	private final boolean pauseOnScroll;
	private final boolean pauseOnFling;
	private final OnScrollListener externalListener;

	/**
	 * Constructor
	 *
	 * @param imageLoader   {@linkplain ImageLoader} instance for controlling
	 * @param pauseOnScroll Whether {@linkplain ImageLoader#pause() pause ImageLoader} during touch scrolling
	 * @param pauseOnFling  Whether {@linkplain ImageLoader#pause() pause ImageLoader} during fling
	 */
	public PauseOnScrollListener(ImageLoader imageLoader, boolean pauseOnScroll, boolean pauseOnFling) {
		this(imageLoader, pauseOnScroll, pauseOnFling, null);
	}

	/**
	 * Constructor
	 *
	 * @param imageLoader    {@linkplain ImageLoader} instance for controlling
	 * @param pauseOnScroll  Whether {@linkplain ImageLoader#pause() pause ImageLoader} during touch scrolling
	 * @param pauseOnFling   Whether {@linkplain ImageLoader#pause() pause ImageLoader} during fling
	 * @param customListener Your custom {@link OnScrollListener} for {@linkplain AbsListView list view} which also
	 *                       will be get scroll events
	 */
	public PauseOnScrollListener(ImageLoader imageLoader, boolean pauseOnScroll, boolean pauseOnFling,
			OnScrollListener customListener) {
		this.imageLoader = imageLoader;
		this.pauseOnScroll = pauseOnScroll;
		this.pauseOnFling = pauseOnFling;
		externalListener = customListener;
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		switch (scrollState) {
			case OnScrollListener.SCROLL_STATE_IDLE:
				imageLoader.resume();
				break;
			case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
				if (pauseOnScroll) {
					imageLoader.pause();
				}
				break;
			case OnScrollListener.SCROLL_STATE_FLING:
				if (pauseOnFling) {
					imageLoader.pause();
				}
				break;
		}
		if (externalListener != null) {
			externalListener.onScrollStateChanged(view, scrollState);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (externalListener != null) {
			externalListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

```
这个类其实也没什么可讲的,就是根据状态变化,是否暂停加载,滑动结束后再启动加载,
在使用的时候.其实对于RecycleView是否可用,我没事试过,但是它提供了一个思路,一个实现滑动暂停加载的思路,RecycleView也有监听滑动的方法,再写一个类似的就ok了

其实最重要的是Imageloader是如何完成暂停和继续逻辑的

### Imageloader.pause和resume

- Imagelader
```java
  public void pause() {
        engine.pause();
    }

    /**
     * Resumes waiting "load&display" tasks
     */
    public void resume() {
        engine.resume();
    }
```
可以看到,所有的操作又转到了ImageLoaderEngine

- ImageLoaderEngine

```java
 	private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean networkDenied = new AtomicBoolean(false);
	private final Object pauseLock = new Object();
 void pause() {
        paused.set(true);
    }

    /**
     * Resumes engine work. Paused "load&display" tasks will continue its work.
     */
    void resume() {
        paused.set(false);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }
	
	  AtomicBoolean getPause() {
        return paused;
    }
```

暂停的,只是设置了一下标志,唤醒,则是通过lock,唤醒所有在等待的线程

那么到底是在哪里暂停的呢

- LoadAndDisplayImageTask

```java

  	@Override
    public void run() {
       	if (waitIfPaused()) return;
		....
	}
 /**
     * 任务是否暂停
     *
     * @return <b>true</b> - if task should be interrupted; <b>false</b> - otherwise
     */
    private boolean waitIfPaused() {
        AtomicBoolean pause = engine.getPause();
        if (pause.get()) {
            synchronized (engine.getPauseLock()) {
                if (pause.get()) {
                    L.d(LOG_WAITING_FOR_RESUME, memoryCacheKey);
                    try {
					//核心代码,本线程等待
                        engine.getPauseLock().wait();
                    } catch (InterruptedException e) {
                        L.e(LOG_TASK_INTERRUPTED, memoryCacheKey);
                        return true;
                    }
                    L.d(LOG_RESUME_AFTER_PAUSE, memoryCacheKey);
                }
            }
        }
		//如果view已经被回收或者被重用,那么暂停
        return isTaskNotActual();
    }
```
到这里就已近很明白了,,其实Imagloader并不能做到中断加载的线程,而是,在加载之前判断一下是否暂停,并不会影响已近在加载的东西,不过这样,也已近做的很好了,至少滑动后,才会加载显示的图片,不用加载

PS :最近项目中遇到一个问题,重写GridView嵌套进ScrollView中,如果重写的是GridView的测量方法,会破坏GrideView的重用机制,导致内存占用过多,所以还是使用头布局/尾部局,或者ViewType来做比较好


### 总结

暂停和重新开水线程,用到了同步代码块和对象监视器,下面是一个参考,可以参照着看一下
http://xiemingmei.iteye.com/blog/1073940