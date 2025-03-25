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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.ColorInt;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import java.util.Locale;

/**
 * This class draws a panel which which will be filled with a color which can be set. It can be used to show the
 * currently selected color which you will get from the {@link ColorPickerView}.
 */
public class ColorPanelView extends View {

  private final static int DEFAULT_BORDER_COLOR = 0xFF6E6E6E;

  private Drawable alphaPattern;
  private Paint borderPaint;
  private Paint colorPaint;
  private Paint alphaPaint;
  private Paint originalPaint;
  private Rect drawingRect;
  private Rect colorRect;
  private RectF centerRect = new RectF();
  private boolean showOldColor;

  /* The width in pixels of the border surrounding the color panel. */
  private int borderWidthPx;
  private int borderColor = DEFAULT_BORDER_COLOR;
  private int color = Color.BLACK;
  private int shape;

  public ColorPanelView(Context context) {
    this(context, null);
  }

  public ColorPanelView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ColorPanelView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs);
  }

  @Override public Parcelable onSaveInstanceState() {
    Bundle state = new Bundle();
    state.putParcelable("instanceState", super.onSaveInstanceState());
    state.putInt("color", color);
    return state;
  }

  @Override public void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Bundle bundle = (Bundle) state;
      color = bundle.getInt("color");
      state = bundle.getParcelable("instanceState");
    }
    super.onRestoreInstanceState(state);
  }

  private void init(Context context, AttributeSet attrs) {
    TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ColorPanelView);
    shape = a.getInt(R.styleable.ColorPanelView_cpv_colorShape, ColorShape.CIRCLE);
    showOldColor = a.getBoolean(R.styleable.ColorPanelView_cpv_showOldColor, false);
    if (showOldColor && shape != ColorShape.CIRCLE) {
      throw new IllegalStateException("Color preview is only available in circle mode");
    }
    borderColor = a.getColor(R.styleable.ColorPanelView_cpv_borderColor, DEFAULT_BORDER_COLOR);
    a.recycle();
    if (borderColor == DEFAULT_BORDER_COLOR) {
      // If no specific border color has been set we take the default secondary text color as border/slider color.
      // Thus it will adopt to theme changes automatically.
      final TypedValue value = new TypedValue();
      TypedArray typedArray =
          context.obtainStyledAttributes(value.data, new int[] { android.R.attr.textColorSecondary });
      borderColor = typedArray.getColor(0, borderColor);
      typedArray.recycle();
    }
    borderWidthPx = DrawingUtils.dpToPx(context, 1);
    borderPaint = new Paint();
    borderPaint.setAntiAlias(true);
    colorPaint = new Paint();
    colorPaint.setAntiAlias(true);
    if (showOldColor) {
      originalPaint = new Paint();
    }
    if (shape == ColorShape.CIRCLE) {
      Bitmap bitmap = ((BitmapDrawable) context.getResources().getDrawable(R.drawable.cpv_alpha)).getBitmap();
      alphaPaint = new Paint();
      alphaPaint.setAntiAlias(true);
      BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
      alphaPaint.setShader(shader);
    }
  }

  @Override protected void onDraw(Canvas canvas) {
    borderPaint.setColor(borderColor);
    colorPaint.setColor(color);
    if (shape == ColorShape.SQUARE) {
      if (borderWidthPx > 0) {
        canvas.drawRect(drawingRect, borderPaint);
      }
      if (alphaPattern != null) {
        alphaPattern.draw(canvas);
      }
      canvas.drawRect(colorRect, colorPaint);
    } else if (shape == ColorShape.CIRCLE) {
      final int outerRadius = getMeasuredWidth() / 2;
      if (borderWidthPx > 0) {
        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, outerRadius, borderPaint);
      }
      if (Color.alpha(color) < 255) {
        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, outerRadius - borderWidthPx, alphaPaint);
      }
      if (showOldColor) {
        canvas.drawArc(centerRect, 90, 180, true, originalPaint);
        canvas.drawArc(centerRect, 270, 180, true, colorPaint);
      } else {
        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, outerRadius - borderWidthPx, colorPaint);
      }
    }
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (shape == ColorShape.SQUARE) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
    } else if (shape == ColorShape.CIRCLE) {
      super.onMeasure(widthMeasureSpec, widthMeasureSpec);
      setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (shape == ColorShape.SQUARE || showOldColor) {
      drawingRect = new Rect();
      drawingRect.left = getPaddingLeft();
      drawingRect.right = w - getPaddingRight();
      drawingRect.top = getPaddingTop();
      drawingRect.bottom = h - getPaddingBottom();
      if (showOldColor) {
        setUpCenterRect();
      } else {
        setUpColorRect();
      }
    }
  }

  private void setUpCenterRect() {
    final Rect dRect = drawingRect;
    int left = dRect.left + borderWidthPx;
    int top = dRect.top + borderWidthPx;
    int bottom = dRect.bottom - borderWidthPx;
    int right = dRect.right - borderWidthPx;
    centerRect = new RectF(left, top, right, bottom);
  }

  private void setUpColorRect() {
    final Rect dRect = drawingRect;
    int left = dRect.left + borderWidthPx;
    int top = dRect.top + borderWidthPx;
    int bottom = dRect.bottom - borderWidthPx;
    int right = dRect.right - borderWidthPx;
    colorRect = new Rect(left, top, right, bottom);
    alphaPattern = new AlphaPatternDrawable(DrawingUtils.dpToPx(getContext(), 4));
    alphaPattern.setBounds(Math.round(colorRect.left), Math.round(colorRect.top), Math.round(colorRect.right),
        Math.round(colorRect.bottom));
  }

  /**
   * Get the color currently show by this view.
   *
   * @return the color value
   */
  public int getColor() {
    return color;
  }

  /**
   * Set the color that should be shown by this view.
   *
   * @param color the color value
   */
  public void setColor(int color) {
    this.color = color;
    invalidate();
  }

  /**
   * Set the original color. This is only used for previewing colors.
   *
   * @param color The original color
   */
  public void setOriginalColor(@ColorInt int color) {
    if (originalPaint != null) {
      originalPaint.setColor(color);
    }
  }

  /**
   * @return the color of the border surrounding the panel.
   */
  public int getBorderColor() {
    return borderColor;
  }

  /**
   * Set the color of the border surrounding the panel.
   *
   * @param color the color value
   */
  public void setBorderColor(int color) {
    borderColor = color;
    invalidate();
  }

  /**
   * Get the shape
   *
   * @return Either {@link ColorShape#SQUARE} or {@link ColorShape#CIRCLE}.
   */
  @ColorShape public int getShape() {
    return shape;
  }

  /**
   * Set the shape.
   *
   * @param shape Either {@link ColorShape#SQUARE} or {@link ColorShape#CIRCLE}.
   */
  public void setShape(@ColorShape int shape) {
    this.shape = shape;
    invalidate();
  }

  /**
   * Show a toast message with the hex color code below the view.
   */
  public void showHint() {
    final int[] screenPos = new int[2];
    final Rect displayFrame = new Rect();
    getLocationOnScreen(screenPos);
    getWindowVisibleDisplayFrame(displayFrame);
    final Context context = getContext();
    final int width = getWidth();
    final int height = getHeight();
    final int midy = screenPos[1] + height / 2;
    int referenceX = screenPos[0] + width / 2;
    if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_LTR) {
      final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
      referenceX = screenWidth - referenceX; // mirror
    }
    StringBuilder hint = new StringBuilder("#");
    if (Color.alpha(color) != 255) {
      hint.append(Integer.toHexString(color).toUpperCase(Locale.ENGLISH));
    } else {
      hint.append(String.format("%06X", 0xFFFFFF & color).toUpperCase(Locale.ENGLISH));
    }
    Toast cheatSheet = Toast.makeText(context, hint.toString(), Toast.LENGTH_SHORT);
    if (midy < displayFrame.height()) {
      // Show along the top; follow action buttons
      cheatSheet.setGravity(Gravity.TOP | GravityCompat.END, referenceX, screenPos[1] + height - displayFrame.top);
    } else {
      // Show along the bottom center
      cheatSheet.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, height);
    }
    cheatSheet.show();
  }
}
