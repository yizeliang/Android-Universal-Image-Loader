## ImageLoader源码解析(三) 线程调度

### 1 默认实现

iamgloader提供了一个默认线程池实现

- DefaultConfigurationFactory.createTaskDistributor
```java
  /**
     * 创建默认的 ThreadPoolExecutor
     * Creates default implementation of task distributor
     */
    public static Executor createTaskDistributor() {
        return Executors.newCachedThreadPool(createThreadFactory(Thread.NORM_PRIORITY, "uil-pool-d-"));
    }
```
其实就一行代码,创建一个缓存线程池,传入一个TreadFactory

先看一下newCachedThreadPool这个方法

```java
  public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>(),
                                      threadFactory);
    }
```
看一下这个方法的参数注解
- ThreadPoolExecutor
```java
/**
* corePoolSize： 线程池维护线程的最少数量

* maximumPoolSize：线程池维护线程的最大数量

* keepAliveTime： 线程池维护线程所允许的空闲时间

* unit： 线程池维护线程所允许的空闲时间的单位

* workQueue： 线程池所使用的缓冲队列

* handler： 线程池对拒绝任务的处理策略
*/
ThreadPoolExecutor (int corePoolSize, 
                int maximumPoolSize, 
                long keepAliveTime, 
                TimeUnit unit, 
                BlockingQueue<Runnable> workQueue, 
                ThreadFactory threadFactory)

```
由上可以看出,咱们其实就是创建了一个 最小为0,最大为int最大值大小的同步队列的线程池

这里有一个类,就是ThreadFactory,咱们看一下这个类

先看一下接口

- ThreadFactory
```java
public interface ThreadFactory {

    /**
     * Constructs a new {@code Thread}.  Implementations may also initialize
     * priority, name, daemon status, {@code ThreadGroup}, etc.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread, or {@code null} if the request to
     *         create a thread is rejected
     */
    Thread newThread(Runnable r);
}
```
通过注释和方法名就看的出来,通过怎么样的方式生成一个线程,可以做一些特定的初始化
- DefaultConfigurationFactory.DefaultThreadFactory

```java

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

```
其实上面无非就是 对线程做了一系列的初始化操作

这就是默认的实现了



### 2 自定义缓存线程池的实现


看过默认实现,貌似也不是很难,就是对线程做下处理,构建一个线程池就OK了

那么自定义配置与默认有哪些区别呢,,自定义其实可以设置的属性包含以下3个方面
1 线程优先级
2 线程池实现队列
3 线程池的大小
其中优先级和大小很容易就能够控制,最复杂的是 队列的实现,Imagloaer提供了两种队列来处理线程池,LILO和LIFO,其实就是链表和栈的概念

#### 2.1 基础知识点

这里面涉及到几个类比较重要

- Collection接口,继承Iterator(迭代器)接口,这个大家应该熟悉,List就是实现了这个接口,集合概念
- Queue队列类,实现Collection接口,方法我来注释一下吧
```java
public interface Queue<E> extends Collection<E> {
    /**
     * 插入元素,如果不可以,抛异常
     */
    boolean add(E e);

    /**
	* 插入一个元素,如果不可以,返回false,否则true
     */
    boolean offer(E e);

    /**
	* 与poll唯一的区别是,空队列抛异常
     */
    E remove();

    /**
     * 检索并移除头结点,空队列返回null
     */
    E poll();

    /**
	* 与peek唯一的区别是,如果这个队列是空的,抛出异常
     */
    E element();

    /**
     * 检索头结点,如果队列为空,返回null
     */
    E peek();
}

```

- Deque 双端队列,相较于Queue,加入了一些前后端操作(addFirst之类的)和Iterator迭代器的操作
- BlockingQueue和BlockingDeque,阻塞式队列和阻塞式双端队列,与Queue和Deque的不同就是在一些情况下,运行线程等待
拿BlockingQueue举例:如果BlockingQueue是空的，从BlockingQueue取东西的操作将会被阻断进入等待状态，直到BlockingQueue进了东西才会被唤醒，同样，如果BlockingQueue是满的，任何试图往里存东西的操作也会被阻断进入等待状态，直到BlockingQueue里有空间时才会被唤醒继续操作
参考:http://blog.csdn.net/caomiao2006/article/details/46676205
http://blog.csdn.net/longeremmy/article/details/8225989
http://www.cnblogs.com/skywang12345/p/3503480.html


能力有限,只能写这么多了,下面分析一下ImageLoader中的实现吧


#### 2.2Imagloader中的实现

<p align=center>
<img src="https://ooo.0o0.ooo/2017/06/10/593b9ba38bf58.png" alt="QQ截图20170610151116.png" title="QQ截图20170610151116.png" width=60% />
</p>

这是Imageloader中提供的队列类图
可以看到 LIFOLinkedBlockingDeque 是集成LinkedBlockingDeque的,我们去看一下代码

- LIFOLinkedBlockingDeque

```java
public class LIFOLinkedBlockingDeque<T> extends LinkedBlockingDeque<T> {

	private static final long serialVersionUID = -4114786347960826192L;

	@Override
	public boolean offer(T e) {
		return super.offerFirst(e);
	}

	@Override
	public T remove() {
		return super.removeFirst();
	}
}

```
可以看到,它只是重写了两个方法,做的事情就是 添加到队列顶端,删除也是删除顶端的节点,栈的概念

下面再看LinkedBlockingDeque
- LinkedBlockingDeque

```java

public class LinkedBlockingDeque<E>
        extends AbstractQueue<E>
        implements BlockingDeque<E>, java.io.Serializable {

    /**
     * 双端队列(链表) 节点类
     */
    static final class Node<E> {
        /**
         * 元素,移出后为null
         */
        E item;

        /**
         * 前置节点,
         * 空代表没有前置节点,
         * 如果是自己,则意味着只有一个元素,自己的前置节点是自己
         */
        Node<E> prev;

        /**
         * 后置节点,与前置节点定义类似
         */
        Node<E> next;

        /**
         * 构造函数
         *
         * @param x
         */
        Node(E x) {
            item = x;
        }
    }

    /**
     * 第一个节点
     * Pointer to first node.
     * Invariant: (first == null && last == null) ||
     * (first.prev == null && first.item != null)
     */
    transient Node<E> first;

    /**
     * 最后一个节点
     * Pointer to last node.
     * Invariant: (first == null && last == null) ||
     * (last.next == null && last.item != null)
     */
    transient Node<E> last;

    /**
     * 队列元素数量
     * Number of items in the deque
     */
    private transient int count;

    /**
     * 队列容量
     * Maximum number of items in the deque
     */
    private final int capacity;

    /**
     * 锁,详情百度
     * Main lock guarding all access
     */
    final ReentrantLock lock = new ReentrantLock();

    /**
     * 空队列 阻塞条件,针对ReentrantLock
     */
    private final Condition notEmpty = lock.newCondition();

    /**
     * 非满 队列阻塞条件,针对ReentrantLock
     * 如果有线程想要插入一个元素,但是队列节点达到最大值,则线程等待
     * 如果这个时候移出了一个元素,需要用这个锁唤醒正在等待的那个线程
     */
    private final Condition notFull = lock.newCondition();

    /**
     * Creates a {@code LinkedBlockingDeque} with a capacity of
     * {@link Integer#MAX_VALUE}.
     */
    public LinkedBlockingDeque() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates a {@code LinkedBlockingDeque} with the given (fixed) capacity.
     *
     * @param capacity 队列容量,小于0抛异常
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public LinkedBlockingDeque(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    /**
     * 用一个集合来初始化队列,把元素放入到队列中,不可以则抛异常
     */
    public LinkedBlockingDeque(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        final ReentrantLock lock = this.lock;
        lock.lock(); // Never contended, but necessary for visibility
        try {
            for (E e : c) {
                if (e == null)
                    throw new NullPointerException();
                if (!linkLast(new Node<E>(e)))
                    throw new IllegalStateException("Deque full");
            }
        } finally {
            lock.unlock();
        }
    }

    //以下为 基本的链接和链接操作,只有持有锁的时候才可以做

    /**
     * 将一个节点设置为第一个节点,如果node为空,返回false
     */
    private boolean linkFirst(Node<E> node) {
        // assert lock.isHeldByCurrentThread();
        if (count >= capacity)
            return false;
        Node<E> f = first;
        node.next = f;
        first = node;
        if (last == null)
            last = node;
        else
            f.prev = node;
        ++count;
        notEmpty.signal();
        return true;
    }

    /**
     * 设置一个节点为最后一个节点
     */
    private boolean linkLast(Node<E> node) {
        // assert lock.isHeldByCurrentThread();
        if (count >= capacity)
            return false;
        Node<E> l = last;
        node.prev = l;
        last = node;
        if (first == null)
            first = node;
        else
            l.next = node;
        ++count;
        notEmpty.signal();
        return true;
    }

    /**
     * 移出并且返回第一个节点,如果为空返回null
     * 如果有第二个节点,第一个节点指向第二个节点
     * 唤醒正在等待的线程(如果有)
     */
    private E unlinkFirst() {
        // assert lock.isHeldByCurrentThread();
        Node<E> f = first;
        if (f == null)
            return null;
        Node<E> n = f.next;
        E item = f.item;
        f.item = null;
        f.next = f; // help GC
        first = n;
        if (n == null)
            last = null;
        else
            n.prev = null;
        --count;
        //唤醒一个正在等待的线程
        notFull.signal();
        return item;
    }

    /**
     * Removes and returns last element, or null if empty.
     */
    private E unlinkLast() {
        // assert lock.isHeldByCurrentThread();
        Node<E> l = last;
        if (l == null)
            return null;
        Node<E> p = l.prev;
        E item = l.item;
        l.item = null;
        l.prev = l; // help GC
        last = p;
        if (p == null)
            first = null;
        else
            p.next = null;
        --count;
        notFull.signal();
        return item;
    }

    /**
     * 移出一个节点
     * Unlinks x.
     */
    void unlink(Node<E> x) {
        // assert lock.isHeldByCurrentThread();
        Node<E> p = x.prev;
        Node<E> n = x.next;
        if (p == null) {
            unlinkFirst();
        } else if (n == null) {
            unlinkLast();
        } else {
            p.next = n;
            n.prev = p;
            x.item = null;
            // Don't mess with x's links.  They may still be in use by
            // an iterator.
            --count;
            notFull.signal();
        }
    }

    // 以下是 BlockingDeque中的操作的方法
	....
	//以下是对一些操作的支持
	....
	}

```

上面记录了一下基本操作,重写的BlockingDeque中的方法,都是获得锁,然后操作,然后释放锁,就不写注释了,里面有些方法

在BlockingDeque,有对ReentrantLock的高级用法,我了解不深,提供一个连接,供大家参考

ReentrantLock 类实现了Lock ，它拥有与 synchronized 相同的并发性和内存语义，但是添加了类似锁投票、定时锁等候和可中断锁等候的一些特性。此外，它还提供了在激烈争用情况下更佳的性能。（换句话说，当许多线程都想访问共享资源时，JVM 可以花更少的时候来调度线程，把更多时间用在执行线程上。

Condition 
线程之间的通信。Condition 将 Object 监视器方法（wait、notify 和 notifyAll）分解成截然不同的对象，以便通过将这些对象与任意 Lock 实现组合使用。

- ReentrantLock和Condition更多参考:
http://blog.csdn.net/vernonzheng/article/details/8288251
http://blog.csdn.net/u013159433/article/details/51407320
http://blog.csdn.net/u010142437/article/details/42495213(Condition 方法介绍)

### 3 知识点汇总

Imagloader已经解析的差不多了,还要一些浅显的我就没有写,基本上属于那种看 单词就知道什么意思的,这里汇总一下Imagloaer里面用的东西吧
- 设计模式
	- Builder模式
	- 单利模式
	- 工厂模式,好吧,也算吧
	- 抽象接口提供灵活性和可扩展性,这个貌似是三大原则之一,对不起,我一个原则的名字都没有记住

- 基础知识
	- ReentrantLock和Condition
	- Executor和ThreadPoolExecutor以及ExecutorService和Runnable
	- LinkedBlockingDeque,Deque,Queue,Collection
	- Bitmap和Drawable(在display中体现的淋漓尽致,虽然我没有分析)
	

### 4 总结

真是不看源码不知道自己的基础有多薄弱,前段时间还想着看Android Framwork源码,实在是不知天高地厚

知识的积累,并不是一定非要搞的特别懂,至少有个印象,熟悉一下用法,知道应用场景,也许有一天,你忽然间就明白了,原来这么做是为了实现什么什么,这个类,原理是这个样子啊

下一篇,EventBus3.0源码解析