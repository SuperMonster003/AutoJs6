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
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * Displays a color picker to the user and allow them to select a color. A slider for the alpha channel is also
 * available.
 * Enable it by setting setAlphaSliderVisible(boolean) to true.
 */
public class ColorPickerView extends View {

  private final static int DEFAULT_BORDER_COLOR = 0xFF6E6E6E;
  private final static int DEFAULT_SLIDER_COLOR = 0xFFBDBDBD;

  private final static int HUE_PANEL_WDITH_DP = 30;
  private final static int ALPHA_PANEL_HEIGH_DP = 20;
  private final static int PANEL_SPACING_DP = 10;
  private final static int CIRCLE_TRACKER_RADIUS_DP = 5;
  private final static int SLIDER_TRACKER_SIZE_DP = 4;
  private final static int SLIDER_TRACKER_OFFSET_DP = 2;

  /**
   * The width in pixels of the border
   * surrounding all color panels.
   */
  private final static int BORDER_WIDTH_PX = 1;

  public int lastColor;

    /**
   * The width in px of the hue panel.
   */
  private int huePanelWidthPx;
  /**
   * The height in px of the alpha panel
   */
  private int alphaPanelHeightPx;
  /**
   * The distance in px between the different
   * color panels.
   */
  private int panelSpacingPx;
  /**
   * The radius in px of the color palette tracker circle.
   */
  private int circleTrackerRadiusPx;
  /**
   * The px which the tracker of the hue or alpha panel
   * will extend outside of its bounds.
   */
  private int sliderTrackerOffsetPx;
  /**
   * Height of slider tracker on hue panel,
   * width of slider on alpha panel.
   */
  private int sliderTrackerSizePx;

  private Paint satValPaint;
  private Paint satValTrackerPaint;

  private Paint alphaPaint;
  private Paint alphaTextPaint;
  private Paint hueAlphaTrackerPaint;

  private Paint borderPaint;

  private Shader valShader;
  private Shader satShader;
  private Shader alphaShader;

  /*
   * We cache a bitmap of the sat/val panel which is expensive to draw each time.
   * We can reuse it when the user is sliding the circle picker as long as the hue isn't changed.
   */
  private BitmapCache satValBackgroundCache;
  /* We cache the hue background to since its also very expensive now. */
  private BitmapCache hueBackgroundCache;

  /* Current values */
  private int alpha = 0xff;
  private float hue = 360f;
  private float sat = 0f;
  private float val = 0f;

  private boolean showAlphaPanel = false;
  private String alphaSliderText = null;
  private int sliderTrackerColor = DEFAULT_SLIDER_COLOR;
  private int borderColor = DEFAULT_BORDER_COLOR;

  /**
   * Minimum required padding. The offset from the
   * edge we must have or else the finger tracker will
   * get clipped when it's drawn outside of the view.
   */
  private int mRequiredPadding;

  /**
   * The Rect in which we are allowed to draw.
   * Trackers can extend outside slightly,
   * due to the required padding we have set.
   */
  private Rect drawingRect;

  private Rect satValRect;
  private Rect hueRect;
  private Rect alphaRect;

  private Point startTouchPoint = null;

  private AlphaPatternDrawable alphaPatternDrawable;
  private OnColorChangedListener onColorChangedListener;

  public ColorPickerView(Context context) {
    this(context, null);
  }

  public ColorPickerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ColorPickerView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs);
  }

  @Override public Parcelable onSaveInstanceState() {
    Bundle state = new Bundle();
    state.putParcelable("instanceState", super.onSaveInstanceState());
    state.putInt("alpha", alpha);
    state.putFloat("hue", hue);
    state.putFloat("sat", sat);
    state.putFloat("val", val);
    state.putBoolean("show_alpha", showAlphaPanel);
    state.putString("alpha_text", alphaSliderText);

    return state;
  }

  @Override public void onRestoreInstanceState(Parcelable state) {

    if (state instanceof Bundle) {
      Bundle bundle = (Bundle) state;

      alpha = bundle.getInt("alpha");
      hue = bundle.getFloat("hue");
      sat = bundle.getFloat("sat");
      val = bundle.getFloat("val");
      showAlphaPanel = bundle.getBoolean("show_alpha");
      alphaSliderText = bundle.getString("alpha_text");

      state = bundle.getParcelable("instanceState");
    }
    super.onRestoreInstanceState(state);
  }

  private void init(Context context, AttributeSet attrs) {
    //Load those if set in xml resource file.
    TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ColorPickerView);
    showAlphaPanel = a.getBoolean(R.styleable.ColorPickerView_cpv_alphaChannelVisible, false);
    alphaSliderText = a.getString(R.styleable.ColorPickerView_cpv_alphaChannelText);
    sliderTrackerColor = a.getColor(R.styleable.ColorPickerView_cpv_sliderColor, 0xFFBDBDBD);
    borderColor = a.getColor(R.styleable.ColorPickerView_cpv_borderColor, 0xFF6E6E6E);
    a.recycle();

    applyThemeColors(context);

    huePanelWidthPx = DrawingUtils.dpToPx(getContext(), HUE_PANEL_WDITH_DP);
    alphaPanelHeightPx = DrawingUtils.dpToPx(getContext(), ALPHA_PANEL_HEIGH_DP);
    panelSpacingPx = DrawingUtils.dpToPx(getContext(), PANEL_SPACING_DP);
    circleTrackerRadiusPx = DrawingUtils.dpToPx(getContext(), CIRCLE_TRACKER_RADIUS_DP);
    sliderTrackerSizePx = DrawingUtils.dpToPx(getContext(), SLIDER_TRACKER_SIZE_DP);
    sliderTrackerOffsetPx = DrawingUtils.dpToPx(getContext(), SLIDER_TRACKER_OFFSET_DP);

    mRequiredPadding = getResources().getDimensionPixelSize(R.dimen.cpv_required_padding);

    initPaintTools();

    //Needed for receiving trackball motion events.
    setFocusable(true);
    setFocusableInTouchMode(true);
  }

  private void applyThemeColors(Context c) {
    // If no specific border/slider color has been
    // set we take the default secondary text color
    // as border/slider color. Thus it will adopt
    // to theme changes automatically.

    final TypedValue value = new TypedValue();
    TypedArray a = c.obtainStyledAttributes(value.data, new int[] { android.R.attr.textColorSecondary });

    if (borderColor == DEFAULT_BORDER_COLOR) {
      borderColor = a.getColor(0, DEFAULT_BORDER_COLOR);
    }

    if (sliderTrackerColor == DEFAULT_SLIDER_COLOR) {
      sliderTrackerColor = a.getColor(0, DEFAULT_SLIDER_COLOR);
    }

    a.recycle();
  }

  private void initPaintTools() {

    satValPaint = new Paint();
    satValTrackerPaint = new Paint();
    hueAlphaTrackerPaint = new Paint();
    alphaPaint = new Paint();
    alphaTextPaint = new Paint();
    borderPaint = new Paint();

    satValTrackerPaint.setStyle(Style.STROKE);
    satValTrackerPaint.setStrokeWidth(DrawingUtils.dpToPx(getContext(), 2));
    satValTrackerPaint.setAntiAlias(true);

    hueAlphaTrackerPaint.setColor(sliderTrackerColor);
    hueAlphaTrackerPaint.setStyle(Style.STROKE);
    hueAlphaTrackerPaint.setStrokeWidth(DrawingUtils.dpToPx(getContext(), 2));
    hueAlphaTrackerPaint.setAntiAlias(true);

    alphaTextPaint.setColor(0xff1c1c1c);
    alphaTextPaint.setTextSize(DrawingUtils.dpToPx(getContext(), 14));
    alphaTextPaint.setAntiAlias(true);
    alphaTextPaint.setTextAlign(Align.CENTER);
    alphaTextPaint.setFakeBoldText(true);
  }

  @Override protected void onDraw(Canvas canvas) {
    if (drawingRect.width() <= 0 || drawingRect.height() <= 0) {
      return;
    }

    drawSatValPanel(canvas);
    drawHuePanel(canvas);
    drawAlphaPanel(canvas);
  }

  private void drawSatValPanel(Canvas canvas) {
    final Rect rect = satValRect;

    if (BORDER_WIDTH_PX > 0) {
      borderPaint.setColor(borderColor);
      canvas.drawRect(drawingRect.left, drawingRect.top, rect.right + BORDER_WIDTH_PX, rect.bottom + BORDER_WIDTH_PX,
          borderPaint);
    }

    if (valShader == null) {
      //Black gradient has either not been created or the view has been resized.
      valShader =
          new LinearGradient(rect.left, rect.top, rect.left, rect.bottom, 0xffffffff, 0xff000000, TileMode.CLAMP);
    }

    //If the hue has changed we need to recreate the cache.
    if (satValBackgroundCache == null || satValBackgroundCache.value != hue) {

      if (satValBackgroundCache == null) {
        satValBackgroundCache = new BitmapCache();
      }

      //We create our bitmap in the cache if it doesn't exist.
      if (satValBackgroundCache.bitmap == null) {
        satValBackgroundCache.bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Config.ARGB_8888);
      }

      //We create the canvas once so we can draw on our bitmap and the hold on to it.
      if (satValBackgroundCache.canvas == null) {
        satValBackgroundCache.canvas = new Canvas(satValBackgroundCache.bitmap);
      }

      int rgb = Color.HSVToColor(new float[] { hue, 1f, 1f });

      satShader = new LinearGradient(rect.left, rect.top, rect.right, rect.top, 0xffffffff, rgb, TileMode.CLAMP);

      ComposeShader mShader = new ComposeShader(valShader, satShader, PorterDuff.Mode.MULTIPLY);
      satValPaint.setShader(mShader);

      // Finally we draw on our canvas, the result will be
      // stored in our bitmap which is already in the cache.
      // Since this is drawn on a canvas not rendered on
      // screen it will automatically not be using the
      // hardware acceleration. And this was the code that
      // wasn't supported by hardware acceleration which mean
      // there is no need to turn it of anymore. The rest of
      // the view will still be hw accelerated.
      satValBackgroundCache.canvas.drawRect(0, 0, satValBackgroundCache.bitmap.getWidth(),
          satValBackgroundCache.bitmap.getHeight(), satValPaint);

      //We set the hue value in our cache to which hue it was drawn with,
      //then we know that if it hasn't changed we can reuse our cached bitmap.
      satValBackgroundCache.value = hue;
    }

    // We draw our bitmap from the cached, if the hue has changed
    // then it was just recreated otherwise the old one will be used.
    canvas.drawBitmap(satValBackgroundCache.bitmap, null, rect, null);

    Point p = satValToPoint(sat, val);

    satValTrackerPaint.setColor(0xff000000);
    canvas.drawCircle(p.x, p.y, circleTrackerRadiusPx - DrawingUtils.dpToPx(getContext(), 1), satValTrackerPaint);

    satValTrackerPaint.setColor(0xffdddddd);
    canvas.drawCircle(p.x, p.y, circleTrackerRadiusPx, satValTrackerPaint);
  }

  private void drawHuePanel(Canvas canvas) {
    final Rect rect = hueRect;

    if (BORDER_WIDTH_PX > 0) {
      borderPaint.setColor(borderColor);

      canvas.drawRect(rect.left - BORDER_WIDTH_PX, rect.top - BORDER_WIDTH_PX, rect.right + BORDER_WIDTH_PX,
          rect.bottom + BORDER_WIDTH_PX, borderPaint);
    }

    if (hueBackgroundCache == null) {
      hueBackgroundCache = new BitmapCache();
      hueBackgroundCache.bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Config.ARGB_8888);
      hueBackgroundCache.canvas = new Canvas(hueBackgroundCache.bitmap);

      int[] hueColors = new int[(int) (rect.height() + 0.5f)];

      // Generate array of all colors, will be drawn as individual lines.
      float h = 360f;
      for (int i = 0; i < hueColors.length; i++) {
        hueColors[i] = Color.HSVToColor(new float[] { h, 1f, 1f });
        h -= 360f / hueColors.length;
      }

      // Time to draw the hue color gradient,
      // its drawn as individual lines which
      // will be quite many when the resolution is high
      // and/or the panel is large.
      Paint linePaint = new Paint();
      linePaint.setStrokeWidth(0);
      for (int i = 0; i < hueColors.length; i++) {
        linePaint.setColor(hueColors[i]);
        hueBackgroundCache.canvas.drawLine(0, i, hueBackgroundCache.bitmap.getWidth(), i, linePaint);
      }
    }

    canvas.drawBitmap(hueBackgroundCache.bitmap, null, rect, null);

    Point p = hueToPoint(hue);

    RectF r = new RectF();
    r.left = rect.left - sliderTrackerOffsetPx;
    r.right = rect.right + sliderTrackerOffsetPx;
    r.top = p.y - (sliderTrackerSizePx / 2);
    r.bottom = p.y + (sliderTrackerSizePx / 2);

    canvas.drawRoundRect(r, 2, 2, hueAlphaTrackerPaint);
  }

  private void drawAlphaPanel(Canvas canvas) {
    /*
     * Will be drawn with hw acceleration, very fast.
     * Also the AlphaPatternDrawable is backed by a bitmap
     * generated only once if the size does not change.
     */

    if (!showAlphaPanel || alphaRect == null || alphaPatternDrawable == null) return;

    final Rect rect = alphaRect;

    if (BORDER_WIDTH_PX > 0) {
      borderPaint.setColor(borderColor);
      canvas.drawRect(rect.left - BORDER_WIDTH_PX, rect.top - BORDER_WIDTH_PX, rect.right + BORDER_WIDTH_PX,
          rect.bottom + BORDER_WIDTH_PX, borderPaint);
    }

    alphaPatternDrawable.draw(canvas);

    float[] hsv = new float[] { hue, sat, val };
    int color = Color.HSVToColor(hsv);
    int acolor = Color.HSVToColor(0, hsv);

    alphaShader = new LinearGradient(rect.left, rect.top, rect.right, rect.top, color, acolor, TileMode.CLAMP);

    alphaPaint.setShader(alphaShader);

    canvas.drawRect(rect, alphaPaint);

    if (alphaSliderText != null && !alphaSliderText.equals("")) {
      canvas.drawText(alphaSliderText, rect.centerX(), rect.centerY() + DrawingUtils.dpToPx(getContext(), 4),
          alphaTextPaint);
    }

    Point p = alphaToPoint(alpha);

    RectF r = new RectF();
    r.left = p.x - (sliderTrackerSizePx / 2);
    r.right = p.x + (sliderTrackerSizePx / 2);
    r.top = rect.top - sliderTrackerOffsetPx;
    r.bottom = rect.bottom + sliderTrackerOffsetPx;

    canvas.drawRoundRect(r, 2, 2, hueAlphaTrackerPaint);
  }

  private Point hueToPoint(float hue) {

    final Rect rect = hueRect;
    final float height = rect.height();

    Point p = new Point();

    p.y = (int) (height - (hue * height / 360f) + rect.top);
    p.x = rect.left;

    return p;
  }

  private Point satValToPoint(float sat, float val) {

    final Rect rect = satValRect;
    final float height = rect.height();
    final float width = rect.width();

    Point p = new Point();

    p.x = (int) (sat * width + rect.left);
    p.y = (int) ((1f - val) * height + rect.top);

    return p;
  }

  private Point alphaToPoint(int alpha) {

    final Rect rect = alphaRect;
    final float width = rect.width();

    Point p = new Point();

    p.x = (int) (width - (alpha * width / 0xff) + rect.left);
    p.y = rect.top;

    return p;
  }

  private float[] pointToSatVal(float x, float y) {

    final Rect rect = satValRect;
    float[] result = new float[2];

    float width = rect.width();
    float height = rect.height();

    if (x < rect.left) {
      x = 0f;
    } else if (x > rect.right) {
      x = width;
    } else {
      x = x - rect.left;
    }

    if (y < rect.top) {
      y = 0f;
    } else if (y > rect.bottom) {
      y = height;
    } else {
      y = y - rect.top;
    }

    result[0] = 1.f / width * x;
    result[1] = 1.f - (1.f / height * y);

    return result;
  }

  private float pointToHue(float y) {

    final Rect rect = hueRect;

    float height = rect.height();

    if (y < rect.top) {
      y = 0f;
    } else if (y > rect.bottom) {
      y = height;
    } else {
      y = y - rect.top;
    }

    float hue = 360f - (y * 360f / height);

    return hue;
  }

  private int pointToAlpha(int x) {

    final Rect rect = alphaRect;
    final int width = rect.width();

    if (x < rect.left) {
      x = 0;
    } else if (x > rect.right) {
      x = width;
    } else {
      x = x - rect.left;
    }

    return 0xff - (x * 0xff / width);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    boolean update = false;

    switch (event.getAction()) {

      case MotionEvent.ACTION_DOWN:
        startTouchPoint = new Point((int) event.getX(), (int) event.getY());
        update = moveTrackersIfNeeded(event);
        break;
      case MotionEvent.ACTION_MOVE:
        update = moveTrackersIfNeeded(event);
        break;
      case MotionEvent.ACTION_UP:
        startTouchPoint = null;
        update = moveTrackersIfNeeded(event);
        break;
    }

    if (update) {
      if (onColorChangedListener != null) {
        onColorChangedListener.onColorChanged(Color.HSVToColor(alpha, new float[] { hue, sat, val }));
      }
      invalidate();
      return true;
    }

    return super.onTouchEvent(event);
  }

  private boolean moveTrackersIfNeeded(MotionEvent event) {
    if (startTouchPoint == null) {
      return false;
    }

    boolean update = false;

    int startX = startTouchPoint.x;
    int startY = startTouchPoint.y;

    if (hueRect.contains(startX, startY)) {
      hue = pointToHue(event.getY());

      update = true;
    } else if (satValRect.contains(startX, startY)) {
      float[] result = pointToSatVal(event.getX(), event.getY());

      sat = result[0];
      val = result[1];

      update = true;
    } else if (alphaRect != null && alphaRect.contains(startX, startY)) {
      alpha = pointToAlpha((int) event.getX());

      update = true;
    }

    return update;
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int finalWidth;
    int finalHeight;

    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);

    int widthAllowed = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
    int heightAllowed = MeasureSpec.getSize(heightMeasureSpec) - getPaddingBottom() - getPaddingTop();

    if (widthMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.EXACTLY) {
      //A exact value has been set in either direction, we need to stay within this size.

      if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
        //The with has been specified exactly, we need to adopt the height to fit.
        int h = (widthAllowed - panelSpacingPx - huePanelWidthPx);

        if (showAlphaPanel) {
          h += panelSpacingPx + alphaPanelHeightPx;
        }

        if (h > heightAllowed) {
          //We can't fit the view in this container, set the size to whatever was allowed.
          finalHeight = heightAllowed;
        } else {
          finalHeight = h;
        }

        finalWidth = widthAllowed;
      } else if (heightMode == MeasureSpec.EXACTLY && widthMode != MeasureSpec.EXACTLY) {
        //The height has been specified exactly, we need to stay within this height and adopt the width.

        int w = (heightAllowed + panelSpacingPx + huePanelWidthPx);

        if (showAlphaPanel) {
          w -= (panelSpacingPx + alphaPanelHeightPx);
        }

        if (w > widthAllowed) {
          //we can't fit within this container, set the size to whatever was allowed.
          finalWidth = widthAllowed;
        } else {
          finalWidth = w;
        }

        finalHeight = heightAllowed;
      } else {
        //If we get here the dev has set the width and height to exact sizes. For example match_parent or 300dp.
        //This will mean that the sat/val panel will not be square but it doesn't matter. It will work anyway.
        //In all other senarios our goal is to make that panel square.

        //We set the sizes to exactly what we were told.
        finalWidth = widthAllowed;
        finalHeight = heightAllowed;
      }
    } else {
      //If no exact size has been set we try to make our view as big as possible
      //within the allowed space.

      //Calculate the needed width to layout using max allowed height.
      int widthNeeded = (heightAllowed + panelSpacingPx + huePanelWidthPx);

      //Calculate the needed height to layout using max allowed width.
      int heightNeeded = (widthAllowed - panelSpacingPx - huePanelWidthPx);

      if (showAlphaPanel) {
        widthNeeded -= (panelSpacingPx + alphaPanelHeightPx);
        heightNeeded += panelSpacingPx + alphaPanelHeightPx;
      }

      boolean widthOk = false;
      boolean heightOk = false;

      if (widthNeeded <= widthAllowed) {
        widthOk = true;
      }

      if (heightNeeded <= heightAllowed) {
        heightOk = true;
      }

      if (widthOk && heightOk) {
        finalWidth = widthAllowed;
        finalHeight = heightNeeded;
      } else if (!heightOk && widthOk) {
        finalHeight = heightAllowed;
        finalWidth = widthNeeded;
      } else if (!widthOk && heightOk) {
        finalHeight = heightNeeded;
        finalWidth = widthAllowed;
      } else {
        finalHeight = heightAllowed;
        finalWidth = widthAllowed;
      }
    }

    setMeasuredDimension(finalWidth + getPaddingLeft() + getPaddingRight(),
        finalHeight + getPaddingTop() + getPaddingBottom());
  }

  private int getPreferredWidth() {
    //Our preferred width and height is 200dp for the square sat / val rectangle.
    int width = DrawingUtils.dpToPx(getContext(), 200);

    return (width + huePanelWidthPx + panelSpacingPx);
  }

  private int getPreferredHeight() {
    int height = DrawingUtils.dpToPx(getContext(), 200);

    if (showAlphaPanel) {
      height += panelSpacingPx + alphaPanelHeightPx;
    }
    return height;
  }

  @Override public int getPaddingTop() {
    return Math.max(super.getPaddingTop(), mRequiredPadding);
  }

  @Override public int getPaddingBottom() {
    return Math.max(super.getPaddingBottom(), mRequiredPadding);
  }

  @Override public int getPaddingLeft() {
    return Math.max(super.getPaddingLeft(), mRequiredPadding);
  }

  @Override public int getPaddingRight() {
    return Math.max(super.getPaddingRight(), mRequiredPadding);
  }

  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    drawingRect = new Rect();
    drawingRect.left = getPaddingLeft();
    drawingRect.right = w - getPaddingRight();
    drawingRect.top = getPaddingTop();
    drawingRect.bottom = h - getPaddingBottom();

    //The need to be recreated because they depend on the size of the view.
    valShader = null;
    satShader = null;
    alphaShader = null;

    // Clear those bitmap caches since the size may have changed.
    satValBackgroundCache = null;
    hueBackgroundCache = null;

    setUpSatValRect();
    setUpHueRect();
    setUpAlphaRect();
  }

  private void setUpSatValRect() {
    //Calculate the size for the big color rectangle.
    final Rect dRect = drawingRect;

    int left = dRect.left + BORDER_WIDTH_PX;
    int top = dRect.top + BORDER_WIDTH_PX;
    int bottom = dRect.bottom - BORDER_WIDTH_PX;
    int right = dRect.right - BORDER_WIDTH_PX - panelSpacingPx - huePanelWidthPx;

    if (showAlphaPanel) {
      bottom -= (alphaPanelHeightPx + panelSpacingPx);
    }

    satValRect = new Rect(left, top, right, bottom);
  }

  private void setUpHueRect() {
    //Calculate the size for the hue slider on the left.
    final Rect dRect = drawingRect;

    int left = dRect.right - huePanelWidthPx + BORDER_WIDTH_PX;
    int top = dRect.top + BORDER_WIDTH_PX;
    int bottom = dRect.bottom - BORDER_WIDTH_PX - (showAlphaPanel ? (panelSpacingPx + alphaPanelHeightPx) : 0);
    int right = dRect.right - BORDER_WIDTH_PX;

    hueRect = new Rect(left, top, right, bottom);
  }

  private void setUpAlphaRect() {

    if (!showAlphaPanel) return;

    final Rect dRect = drawingRect;

    int left = dRect.left + BORDER_WIDTH_PX;
    int top = dRect.bottom - alphaPanelHeightPx + BORDER_WIDTH_PX;
    int bottom = dRect.bottom - BORDER_WIDTH_PX;
    int right = dRect.right - BORDER_WIDTH_PX;

    alphaRect = new Rect(left, top, right, bottom);

    alphaPatternDrawable = new AlphaPatternDrawable(DrawingUtils.dpToPx(getContext(), 4));
    alphaPatternDrawable.setBounds(Math.round(alphaRect.left), Math.round(alphaRect.top), Math.round(alphaRect.right),
        Math.round(alphaRect.bottom));
  }

  /**
   * Set a OnColorChangedListener to get notified when the color
   * selected by the user has changed.
   *
   * @param listener the listener
   */
  public void setOnColorChangedListener(OnColorChangedListener listener) {
    onColorChangedListener = listener;
  }

  /**
   * Get the current color this view is showing.
   *
   * @return the current color.
   */
  public int getColor() {
    return Color.HSVToColor(alpha, new float[] { hue, sat, val });
  }

  /**
   * Set the color the view should show.
   *
   * @param color The color that should be selected. #argb
   */
  public void setColor(int color) {
    setColor(color, false);
  }

  /**
   * Set the color this view should show.
   *
   * @param color The color that should be selected. #argb
   * @param callback If you want to get a callback to your OnColorChangedListener.
   */
  public void setColor(int color, boolean callback) {

    int alpha = Color.alpha(color);
    int red = Color.red(color);
    int blue = Color.blue(color);
    int green = Color.green(color);

    float[] hsv = new float[3];

    Color.RGBToHSV(red, green, blue, hsv);

    this.alpha = alpha;
    hue = hsv[0];
    sat = hsv[1];
    val = hsv[2];

    if (callback && onColorChangedListener != null) {
      onColorChangedListener.onColorChanged(Color.HSVToColor(this.alpha, new float[] { hue, sat, val }));
    }

    invalidate();
  }

  /**
   * Set if the user is allowed to adjust the alpha panel. Default is false.
   * If it is set to false no alpha will be set.
   *
   * @param visible {@code true} to show the alpha slider
   */
  public void setAlphaSliderVisible(boolean visible) {
    if (showAlphaPanel != visible) {
      showAlphaPanel = visible;

      /*
       * Force recreation.
       */
      valShader = null;
      satShader = null;
      alphaShader = null;
      hueBackgroundCache = null;
      satValBackgroundCache = null;

      requestLayout();
    }
  }

  /**
   * Get color of the tracker slider on the hue and alpha panel.
   *
   * @return the color value
   */
  public int getSliderTrackerColor() {
    return sliderTrackerColor;
  }

  /**
   * Set the color of the tracker slider on the hue and alpha panel.
   *
   * @param color a color value
   */
  public void setSliderTrackerColor(int color) {
    sliderTrackerColor = color;
    hueAlphaTrackerPaint.setColor(sliderTrackerColor);
    invalidate();
  }

  /**
   * Get the color of the border surrounding all panels.
   */
  public int getBorderColor() {
    return borderColor;
  }

  /**
   * Set the color of the border surrounding all panels.
   *
   * @param color a color value
   */
  public void setBorderColor(int color) {
    borderColor = color;
    invalidate();
  }

  /**
   * Set the text that should be shown in the
   * alpha slider. Set to null to disable text.
   *
   * @param res string resource id.
   */
  public void setAlphaSliderText(int res) {
    String text = getContext().getString(res);
    setAlphaSliderText(text);
  }

  /**
   * Get the current value of the text
   * that will be shown in the alpha
   * slider.
   *
   * @return the slider text
   */
  public String getAlphaSliderText() {
    return alphaSliderText;
  }

  /**
   * Set the text that should be shown in the
   * alpha slider. Set to null to disable text.
   *
   * @param text Text that should be shown.
   */
  public void setAlphaSliderText(String text) {
    alphaSliderText = text;
    invalidate();
  }

  public interface OnColorChangedListener {

    void onColorChanged(int newColor);
  }

  private class BitmapCache {

    public Canvas canvas;
    public Bitmap bitmap;
    public float value;
  }
}
