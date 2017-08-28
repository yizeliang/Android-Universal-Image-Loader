package com.nostra13.universalimageloader.core.display;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.nostra13.universalimageloader.core.assist.LoadedFrom;
import com.nostra13.universalimageloader.core.display.BitmapDisplayer;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;

import java.lang.reflect.Field;

/**
 * Universal-Image-Loader中RoundedBitmapDisplayer的增强版，可以自定义图片4个角中的指定角为圆角<br/>
 * 主要用来解决4.x系统中平常的 部分角设置为圆角无效的问题<br/>
 * 加入 centcrop方法,目前只支持正方形<br/>
 * 使用方式<br/>
 * 1 网络图片,直接使用ImageLoader设置displayer即可<br/>
 * 注意设置这个imageScaleType(ImageScaleType.NONE),否则可能裁剪失败 <br/>
 * 2 本地图片,直接获取bitmap创建该对象,调用display即可<br/>
 * 原理:对获取的图片进行手动裁剪{@link FlexibleRoundedBitmapDisplayer#convertBitMapCenterCrop}<br/>
 */
public class FlexibleRoundedBitmapDisplayer implements BitmapDisplayer {

    protected int cornerRadius;
    protected int corners;
    private int imageViewWidth = -1;


    public static final int CORNER_TOP_LEFT = 1;
    public static final int CORNER_TOP_RIGHT = 1 << 1;
    public static final int CORNER_BOTTOM_LEFT = 1 << 2;
    public static final int CORNER_BOTTOM_RIGHT = 1 << 3;
    public static final int CORNER_ALL = CORNER_TOP_LEFT | CORNER_TOP_RIGHT
            | CORNER_BOTTOM_LEFT | CORNER_BOTTOM_RIGHT;

    private boolean centerCrop = false;


    private int borderColor = -1;
    private int borderWidth = -1;

    /**
     * border
     */
    private GradientDrawable bgDrawable;

    /**
     * 构造方法说明：设定圆角像素大小，所有角都为圆角
     *
     * @param cornerRadiusPixels 圆角像素大小
     */

    public FlexibleRoundedBitmapDisplayer(int cornerRadiusPixels) {
        this.cornerRadius = cornerRadiusPixels;
        this.corners = CORNER_ALL;
    }

    /**
     * 构造方法说明：设定圆角像素大小，指定角为圆角
     *
     * @param cornerRadiusPixels 圆角像素大小
     * @param corners            自定义圆角
     *                           <p>
     *                           CORNER_NONE　无圆角
     *                           <p>
     *                           CORNER_ALL 全为圆角
     *                           <p>
     *                           CORNER_TOP_LEFT | CORNER_TOP_RIGHT | CORNER_BOTTOM_LEFT |
     *                           CORNER_BOTTOM_RIGHT　指定圆角（选其中若干组合 ）
     */
    public FlexibleRoundedBitmapDisplayer(int cornerRadiusPixels, int corners) {
        this(cornerRadiusPixels, corners, -1);
    }

    public FlexibleRoundedBitmapDisplayer(int cornerRadiusPixels, int corners,
                                          int imageViewWidth) {
        this(cornerRadiusPixels, corners, imageViewWidth, false);
    }

    /**
     * @param cornerRadius
     * @param corners
     * @param imageViewWidth
     * @param centerCrop     如果centerCrop那么imageViewWith必须>0
     */
    public FlexibleRoundedBitmapDisplayer(int cornerRadius, int corners, int imageViewWidth, boolean centerCrop) {
        this.cornerRadius = cornerRadius;
        this.corners = corners;
        this.imageViewWidth = imageViewWidth;
        this.centerCrop = centerCrop;
    }

    /**
     * 设置Border,只能初始化的时候设置
     *
     * @param borderWidth
     * @param borderColor
     */
    public FlexibleRoundedBitmapDisplayer setBorder(int borderWidth, int borderColor) {
        if (bgDrawable != null) {
            return this;
        }
        this.borderWidth = borderWidth;
        this.borderColor = borderWidth;
        return this;
    }

    @Override
    public void display(Bitmap bitmap, ImageAware imageAware,
                        LoadedFrom loadedFrom) {
        if (!(imageAware instanceof ImageViewAware)) {
            throw new IllegalArgumentException(
                    "ImageAware should wrap ImageView. ImageViewAware is expected.");
        }
        if (bitmap == null) {
            return;
        }
        //默认加载图,不进行裁剪处理
        if (loadedFrom == null || loadedFrom == LoadedFrom.DEFAULT_EMPTY_URL
                || loadedFrom == LoadedFrom.DEFAULT_FAIL
                || loadedFrom == LoadedFrom.DEFAULT_LOADING) {
            imageAware.setImageDrawable(new FlexibleRoundedDrawable(bitmap,
                    cornerRadius, corners));
            setBgDrawableToIV(imageAware);
            return;
        }
        if (!centerCrop) {
            imageAware.setImageDrawable(new FlexibleRoundedDrawable(bitmap,
                    cornerRadius, corners));
            setBgDrawableToIV(imageAware);
            return;
        }
        //以下是裁剪处理
        Bitmap convertBitmap = bitmap;
        if (imageViewWidth > 0) {
            convertBitmap = convertBitMapCenterCrop(bitmap, imageViewWidth);
            if (convertBitmap == null) {
                convertBitmap = bitmap;
            }
        }
        imageAware.setImageDrawable(new FlexibleRoundedDrawable(convertBitmap,
                cornerRadius, corners));
        setBgDrawableToIV(imageAware);
    }


    /**
     * 为ImageView设置背景
     *
     * @param imageAware
     */
    private void setBgDrawableToIV(ImageAware imageAware) {
        if (imageAware != null) {
            ImageView wrappedView = (ImageView) imageAware.getWrappedView();
            if (wrappedView != null) {
                if (borderColor > 0 && borderWidth > 0) {
                    wrappedView.setPadding(borderWidth, borderWidth, borderWidth, borderWidth);
                    wrappedView.setBackground(createBgDrawable());
                }
            }
        }
    }


    /**
     * 创建border
     *
     * @return
     */
    private Drawable createBgDrawable() {
        if (bgDrawable != null) {
            return bgDrawable;
        }
        bgDrawable = new GradientDrawable();
        bgDrawable.setStroke(borderWidth, borderColor);

        int notRoundedCorners = corners ^ CORNER_ALL;
        float[] radis = new float[4];
        // 哪个角不是圆角我再把你用矩形画出来
        if ((notRoundedCorners & CORNER_TOP_LEFT) != 0) {
            radis[0] = cornerRadius;
        }
        if ((notRoundedCorners & CORNER_TOP_RIGHT) != 0) {
            radis[1] = cornerRadius;
        }
        if ((notRoundedCorners & CORNER_BOTTOM_LEFT) != 0) {
            radis[2] = cornerRadius;
        }
        if ((notRoundedCorners & CORNER_BOTTOM_RIGHT) != 0) {
            radis[3] = cornerRadius;
        }
        bgDrawable.setCornerRadii(radis);
        return bgDrawable;
    }

    /**
     * 通过反射获取stroke值
     *
     * @param drawable
     * @return
     */
//    private Float getBgDrawableStokeWidth(GradientDrawable drawable) {
//        try {
//            Class object = GradientDrawable.class;
//            Field mStrokePaint = object.getField("mStrokePaint");
//            mStrokePaint.setAccessible(false);
//            Float f = (Float) mStrokePaint.get(drawable);
//            return f;
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        return 0f;
//    }

    /**
     * 转换bitmap为centercropBitmap
     *
     * @param bitmap
     * @return
     */
    private Bitmap convertBitMapCenterCrop(Bitmap bitmap, int edgeLength) {
        if (null == bitmap || edgeLength <= 0) {
            return null;
        }
        Bitmap result = bitmap;
        int widthOrg = bitmap.getWidth();
        int heightOrg = bitmap.getHeight();

        if (widthOrg > edgeLength && heightOrg > edgeLength) {
            // 高和宽都大于 View的大小
            // 压缩到一个最小长度是edgeLength的bitmap
            int longerEdge = (int) (edgeLength * Math.max(widthOrg, heightOrg) / Math
                    .min(widthOrg, heightOrg));
            int scaledWidth = widthOrg > heightOrg ? longerEdge : edgeLength;
            int scaledHeight = widthOrg > heightOrg ? edgeLength : longerEdge;
            Bitmap scaledBitmap;
            try {
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth,
                        scaledHeight, true);
            } catch (Exception e) {
                return null;
            }
            // 从图中截取正中间的正方形部分。
            int xTopLeft = (scaledWidth - edgeLength) / 2;
            int yTopLeft = (scaledHeight - edgeLength) / 2;
            try {
                result = Bitmap.createBitmap(scaledBitmap, xTopLeft, yTopLeft,
                        edgeLength, edgeLength);
                scaledBitmap.recycle();
            } catch (Exception e) {
                return null;
            }
        } else if (widthOrg > edgeLength) {
            // 当宽度大于,高度不大于的时候
            result = Bitmap.createBitmap(bitmap, (widthOrg - edgeLength) / 2,
                    0, edgeLength, heightOrg);

        } else if (heightOrg > edgeLength) {
            // 当宽度小于,高度大于的时候
            result = Bitmap.createBitmap(bitmap, 0,
                    (heightOrg - edgeLength) / 2, widthOrg, edgeLength);
        }

        result = createNewBitmapForSamll(result, edgeLength);
        return result;
    }

    /**
     * 为小的图片添加白色边框,防止发生拉伸
     *
     * @param bitmap
     * @param edgeLength
     * @return Drawable
     *
     */
    public Bitmap createNewBitmapForSamll(Bitmap bitmap, int edgeLength) {
        Bitmap newBitmap = bitmap;
        int width = newBitmap.getWidth();
        int height = newBitmap.getHeight();
        if (width < edgeLength) {
            newBitmap = comBinBitmap(newBitmap, edgeLength, false);
        }

        if (height < edgeLength) {
            newBitmap = comBinBitmap(newBitmap, edgeLength, true);
        }
        return newBitmap;
    }

    /**
     * 真正的拼接方法
     *
     * @param b1
     * @param edgeLength
     * @param top        是否是上下加白色边框,true,上下,false,左右
     * @return
     */
    public Bitmap comBinBitmap(Bitmap b1, int edgeLength, Boolean top) {
        Bitmap newbmp = null;
        Canvas cv = null;

        try {
            if (top) {
                Bitmap tempBitmap = Bitmap.createBitmap(b1.getWidth(),
                        (edgeLength - b1.getHeight()) / 2, Config.ARGB_8888);
                newbmp = Bitmap.createBitmap(b1.getWidth(), edgeLength,
                        Config.ARGB_8888);
                cv = new Canvas(newbmp);
                cv.drawBitmap(tempBitmap, 0, 0, null);
                cv.drawBitmap(b1, 0, tempBitmap.getHeight(), null);
                cv.drawBitmap(tempBitmap, 0,
                        b1.getHeight() + tempBitmap.getHeight(), null);
                cv.save(Canvas.ALL_SAVE_FLAG);
                cv.restore();

            } else {
                Bitmap tempBitmap = Bitmap.createBitmap(
                        (edgeLength - b1.getWidth()) / 2, b1.getHeight(),
                        Config.ARGB_8888);
                newbmp = Bitmap.createBitmap(edgeLength, b1.getHeight(),
                        Config.ARGB_8888);
                cv = new Canvas(newbmp);
                cv.drawBitmap(tempBitmap, 0, 0, null);
                cv.drawBitmap(b1, tempBitmap.getWidth(), 0, null);
                cv.drawBitmap(tempBitmap,
                        tempBitmap.getWidth() + b1.getWidth(), 0, null);
                cv.save(Canvas.ALL_SAVE_FLAG);
                cv.restore();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return b1;
        }

        if (newbmp == null) {
            return b1;
        } else {
            return newbmp;
        }
    }

    public static class FlexibleRoundedDrawable extends Drawable {
        protected final float cornerRadius;

        protected final RectF mRect = new RectF(), mBitmapRect;
        protected final BitmapShader bitmapShader;
        protected final Paint paint;
        private int corners;
        private Bitmap bitmap;

        public FlexibleRoundedDrawable(Bitmap bitmap, int cornerRadius,
                                       int corners) {
            this.cornerRadius = cornerRadius;
            this.corners = corners;
            this.bitmap = bitmap;
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            mBitmapRect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setShader(bitmapShader);
        }


        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mRect.set(0, 0, bounds.width(), bounds.height());
            Matrix shaderMatrix = new Matrix();
            shaderMatrix.setRectToRect(mBitmapRect, mRect,
                    Matrix.ScaleToFit.FILL);
            bitmapShader.setLocalMatrix(shaderMatrix);

        }

        @Override
        public void draw(Canvas canvas) {
            // 先画一个圆角矩形将图片显示为圆角
            canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, paint);
            int notRoundedCorners = corners ^ CORNER_ALL;
            // 哪个角不是圆角我再把你用矩形画出来
            if ((notRoundedCorners & CORNER_TOP_LEFT) != 0) {
                canvas.drawRect(0, 0, cornerRadius, cornerRadius, paint);
            }
            if ((notRoundedCorners & CORNER_TOP_RIGHT) != 0) {
                canvas.drawRect(mRect.right - cornerRadius, 0, mRect.right,
                        cornerRadius, paint);
            }
            if ((notRoundedCorners & CORNER_BOTTOM_LEFT) != 0) {
                canvas.drawRect(0, mRect.bottom - cornerRadius, cornerRadius,
                        mRect.bottom, paint);
            }
            if ((notRoundedCorners & CORNER_BOTTOM_RIGHT) != 0) {
                canvas.drawRect(mRect.right - cornerRadius, mRect.bottom
                        - cornerRadius, mRect.right, mRect.bottom, paint);
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }
    }

    public void setCenterCrop(boolean centerCrop) {
        this.centerCrop = centerCrop;
    }
}