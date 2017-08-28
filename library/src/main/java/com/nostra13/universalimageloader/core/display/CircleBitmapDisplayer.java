/*******************************************************************************
 * Copyright 2015 Sergey Tarasevich
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
package com.nostra13.universalimageloader.core.display;

import android.graphics.Bitmap;
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

import com.nostra13.universalimageloader.core.assist.LoadedFrom;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;

/**
 * Can display bitmap cropped by a circle. This implementation works only with ImageViews wrapped
 * in ImageViewAware.
 * <br />
 * If this implementation doesn't meet your needs then consider
 * <a href="https://github.com/vinc3m1/RoundedImageView">RoundedImageView</a> or
 * <a href="https://github.com/Pkmmte/CircularImageView">CircularImageView</a> projects for usage.
 *
 * @author Qualtagh, Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.9.5
 */
public class CircleBitmapDisplayer implements BitmapDisplayer {

    /**
     * 边框的颜色
     */
    protected final Integer strokeColor;
    /**
     * 边框宽度
     */
    protected final float strokeWidth;

    public CircleBitmapDisplayer() {
        this(null);
    }

    public CircleBitmapDisplayer(Integer strokeColor) {
        this(strokeColor, 0);
    }

    public CircleBitmapDisplayer(Integer strokeColor, float strokeWidth) {
        this.strokeColor = strokeColor;
        this.strokeWidth = strokeWidth;
    }

    @Override
    public void display(Bitmap bitmap, ImageAware imageAware, LoadedFrom loadedFrom) {
        if (!(imageAware instanceof ImageViewAware)) {
            throw new IllegalArgumentException("ImageAware should wrap ImageView. ImageViewAware is expected.");
        }
        imageAware.setImageDrawable(new CircleDrawable(bitmap, strokeColor, strokeWidth));
    }

    public static class CircleDrawable extends Drawable {

        /**
         * 弧度
         */
        protected float radius;
        /**
         * 绘制范围
         */
        protected final RectF mRect = new RectF();
        /**
         * bitmap范围
         */
        protected final RectF mBitmapRect;
        protected final BitmapShader bitmapShader;
        /**
         * 图形画笔
         */
        protected final Paint paint;
        /**
         * 边框画笔
         */
        protected final Paint strokePaint;
        /**
         * 边框宽度
         */
        protected final float strokeWidth;
        /**
         * 边框的弧度
         */
        protected float strokeRadius;

        public CircleDrawable(Bitmap bitmap, Integer strokeColor, float strokeWidth) {

            radius = Math.min(bitmap.getWidth(), bitmap.getHeight()) / 2;

            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mBitmapRect = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());

            paint = new Paint();
            //设置是否使用抗锯齿功能，会消耗较大资源，绘制图形速度会变慢。但是效果更好
            paint.setAntiAlias(true);
            paint.setShader(bitmapShader);
            //如果该项设置为true，则图像在动画进行中会滤掉对Bitmap图像的优化操作，加快显示速度
            paint.setFilterBitmap(true);
            //设定是否使用图像抖动处理，会使绘制出来的图片颜色更加平滑和饱满，图像更加清晰
            paint.setDither(true);
            //如果边框颜色设置了,那么就有边框
            if (strokeColor == null) {
                strokePaint = null;
            } else {
                strokePaint = new Paint();
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setColor(strokeColor);
                strokePaint.setStrokeWidth(strokeWidth);
                strokePaint.setAntiAlias(true);
            }
            this.strokeWidth = strokeWidth;

            strokeRadius = radius - strokeWidth / 2;
        }

        /**
         * 范围发生变化时,可能如果imagview大小发生变化,这里会调用吧
         *
         * @param bounds
         */
        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mRect.set(0, 0, bounds.width(), bounds.height());
            radius = Math.min(bounds.width(), bounds.height()) / 2;
            strokeRadius = radius - strokeWidth / 2;

            //重新设置bitmap的大小去适应新的绘制范围,这里其实就是fitxy处理
            Matrix shaderMatrix = new Matrix();
            /**
             * 设置变换规则,自动计算mBitmapRect->mRect变换的规则<br/>
             * 第三个参数,有FILL,start,end,center,还记得,ImageView的ScaleType吗,对比理解<br/>
             * Fill就是FitXY
             */
            shaderMatrix.setRectToRect(mBitmapRect, mRect, Matrix.ScaleToFit.FILL);
            //为bitmapshader设置变换矩阵
            bitmapShader.setLocalMatrix(shaderMatrix);
        }

        @Override
        public void draw(Canvas canvas) {
            //绘制bitmap
            canvas.drawCircle(radius, radius, radius, paint);
            if (strokePaint != null) {
                //绘制边框
                canvas.drawCircle(radius, radius, strokeRadius, strokePaint);
            }
        }
        //-------------------------下面是通过设置画笔,然后重绘,来支持View的一些设置(透明度,颜色过滤,也可以添加其他的属性支持)-------------------------------

        /**
         * 获取透明度的格式,看一下父类中的注释
         * {@link Drawable#getOpacity() }
         * <p>
         * 只有四个返回值
         * {@link android.graphics.PixelFormat}:
         * {@linkplain android.graphics.PixelFormat#UNKNOWN 未知},
         * {@linkplain android.graphics.PixelFormat#TRANSLUCENT 系统选择支持半透明的格式 bits},
         * {@linkplain android.graphics.PixelFormat#TRANSPARENT 系统选择支持透明度的格式 0-1 }, or
         * {@linkplain android.graphics.PixelFormat#OPAQUE 系统选择不透明格式（不需要alpha位）}.
         *
         * @return
         */
        @Override
        public int getOpacity() {
            //系统选择支持半透明的格式
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
}
