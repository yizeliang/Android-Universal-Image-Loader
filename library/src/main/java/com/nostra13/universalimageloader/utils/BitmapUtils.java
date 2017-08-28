package com.nostra13.universalimageloader.utils;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * bitmap转换工具类
 * Created by YZL on 2017/6/9.
 */
public class BitmapUtils {
    public static Bitmap DrawableToBitmap(Drawable drawable) {
//        Bitmap bitmap = Bitmap.createBitmap(
//
//                drawable.getIntrinsicWidth(),
//
//                drawable.getIntrinsicHeight(),
//
//                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
//                        : Bitmap.Config.RGB_565);
//        Canvas canvas = new Canvas(bitmap);
//        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
//        drawable.draw(canvas);
        BitmapDrawable bd = (BitmapDrawable) drawable;
        return bd.getBitmap();
    }
}
