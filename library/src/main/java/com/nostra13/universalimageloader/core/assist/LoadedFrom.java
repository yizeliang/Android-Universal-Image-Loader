package com.nostra13.universalimageloader.core.assist;

/**
 * Source image loaded from.
 * 图片读取的方式,网络,缓存,内存
 *
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @version 2.0.1 加入默认图片方式
 */
public enum LoadedFrom {
    NETWORK, DISC_CACHE, MEMORY_CACHE,
    /**
     * 默认失败图片
     */
    DEFAULT_FAIL,
    /**
     * 默认空URL图片
     */
    DEFAULT_EMPTY_URL,
    /**
     * 默认加载中图片
     */
    DEFAULT_LOADING
}