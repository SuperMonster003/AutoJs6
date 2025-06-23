/*
 * Copyright (C) 2017 Jared Rummler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jaredrummler.android.colorpicker;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * This drawable will draw a simple white and gray chessboard pattern.
 * It's the pattern you will often see as a background behind a partly transparent image in many applications.
 */
class AlphaPatternDrawable extends Drawable {

  private int rectangleSize = 10;

  private Paint paint = new Paint();
  private Paint paintWhite = new Paint();
  private Paint paintGray = new Paint();

  private int numRectanglesHorizontal;
  private int numRectanglesVertical;

  /**
   * Bitmap in which the pattern will be cached.
   * This is so the pattern will not have to be recreated each time draw() gets called.
   * Because recreating the pattern i rather expensive. I will only be recreated if the size changes.
   */
  private Bitmap bitmap;

  AlphaPatternDrawable(int rectangleSize) {
    this.rectangleSize = rectangleSize;
    paintWhite.setColor(0xFFFFFFFF);
    paintGray.setColor(0xFFCBCBCB);
  }

  @Override public void draw(Canvas canvas) {
    if (bitmap != null && !bitmap.isRecycled()) {
      canvas.drawBitmap(bitmap, null, getBounds(), paint);
    }
  }

  @Override public int getOpacity() {
    return 0;
  }

  @Override public void setAlpha(int alpha) {
    throw new UnsupportedOperationException("Alpha is not supported by this drawable.");
  }

  @Override public void setColorFilter(ColorFilter cf) {
    throw new UnsupportedOperationException("ColorFilter is not supported by this drawable.");
  }

  @Override protected void onBoundsChange(Rect bounds) {
    super.onBoundsChange(bounds);
    int height = bounds.height();
    int width = bounds.width();
    numRectanglesHorizontal = (int) Math.ceil((width / rectangleSize));
    numRectanglesVertical = (int) Math.ceil(height / rectangleSize);
    generatePatternBitmap();
  }

  /**
   * This will generate a bitmap with the pattern as big as the rectangle we were allow to draw on.
   * We do this to chache the bitmap so we don't need to recreate it each time draw() is called since it takes a few
   * milliseconds
   */
  private void generatePatternBitmap() {
    if (getBounds().width() <= 0 || getBounds().height() <= 0) {
      return;
    }

    bitmap = Bitmap.createBitmap(getBounds().width(), getBounds().height(), Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);

    Rect r = new Rect();
    boolean verticalStartWhite = true;
    for (int i = 0; i <= numRectanglesVertical; i++) {
      boolean isWhite = verticalStartWhite;
      for (int j = 0; j <= numRectanglesHorizontal; j++) {
        r.top = i * rectangleSize;
        r.left = j * rectangleSize;
        r.bottom = r.top + rectangleSize;
        r.right = r.left + rectangleSize;
        canvas.drawRect(r, isWhite ? paintWhite : paintGray);
        isWhite = !isWhite;
      }
      verticalStartWhite = !verticalStartWhite;
    }
  }
}
