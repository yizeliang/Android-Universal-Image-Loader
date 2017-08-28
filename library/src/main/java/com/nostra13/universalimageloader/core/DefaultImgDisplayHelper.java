package com.nostra13.universalimageloader.core;

import android.graphics.Bitmap;

import com.nostra13.universalimageloader.core.assist.LoadedFrom;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.utils.BitmapUtils;

/**
 * 默认图片部署类
 * Created by YZL on 2017/6/9.
 */
public class DefaultImgDisplayHelper {

    /**
     * 部署默认图片
     *
     * @param configuration
     * @param options
     * @param imageAware
     * @param loadedFrom
     */
    public static void displayDefaultImg(ImageLoaderConfiguration configuration,
                                         DisplayImageOptions options,
                                         ImageAware imageAware, LoadedFrom loadedFrom) {

        //没有启用
        //针对偶然性bug,目前只遇到过一次
        if (!options.isUseDisplayerDefaultImg()
                && !options.isProcessDefaultImg()) {
            switch (loadedFrom) {
                case DEFAULT_EMPTY_URL:
                    if (options.shouldShowImageForEmptyUri()) {
                        imageAware.setImageDrawable(options.getImageForEmptyUri(configuration.resources));
                    } else {
                        return;
                    }
                    break;
                case DEFAULT_LOADING:
                    if (options.shouldShowImageOnLoading()) {
                        imageAware.setImageDrawable(options.getImageOnLoading(configuration.resources));
                    } else {
                        return;
                    }
                    break;
                case DEFAULT_FAIL:
                    if (options.shouldShowImageOnFail()) {
                        imageAware.setImageDrawable(options.getImageOnFail(configuration.resources));
                    } else {
                        return;
                    }
                    break;
                default:
                    return;
            }
            return;
        }
        //启用之后
        Bitmap bitmap = null;
        switch (loadedFrom) {
            case DEFAULT_EMPTY_URL:
                if (options.shouldShowImageForEmptyUri()) {
                    bitmap = BitmapUtils.DrawableToBitmap(options.getImageForEmptyUri(configuration.resources));
                } else {
                    return;
                }
                break;
            case DEFAULT_LOADING:
                if (options.shouldShowImageOnLoading()) {
                    bitmap = BitmapUtils.DrawableToBitmap(options.getImageOnLoading(configuration.resources));
                } else {
                    return;
                }
                break;
            case DEFAULT_FAIL:
                if (options.shouldShowImageOnFail()) {
                    bitmap = BitmapUtils.DrawableToBitmap(options.getImageOnFail(configuration.resources));
                } else {
                    return;
                }
                break;
            default:
                return;
        }
        if (options.isProcessDefaultImg() && options.shouldProcess()) {
            bitmap = options.getProcessor().process(bitmap);
        }
        if (options.isUseDisplayerDefaultImg() && options.getDisplayer() != null) {
            options.getDisplayer().display(bitmap, imageAware, loadedFrom);
            return;
        }
        imageAware.setImageBitmap(bitmap);
    }
}
