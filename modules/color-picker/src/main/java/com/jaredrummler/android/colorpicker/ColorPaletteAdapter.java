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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import androidx.core.graphics.ColorUtils;

class ColorPaletteAdapter extends BaseAdapter {

  /*package*/ final OnColorSelectedListener listener;
  /*package*/ final int[] colors;
  /*package*/ int selectedPosition;
  /*package*/ int colorShape;

  ColorPaletteAdapter(OnColorSelectedListener listener, int[] colors, int selectedPosition,
      @ColorShape int colorShape) {
    this.listener = listener;
    this.colors = colors;
    this.selectedPosition = selectedPosition;
    this.colorShape = colorShape;
  }

  @Override public int getCount() {
    return colors.length;
  }

  @Override public Object getItem(int position) {
    return colors[position];
  }

  @Override public long getItemId(int position) {
    return position;
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    final ViewHolder holder;
    if (convertView == null) {
      holder = new ViewHolder(parent.getContext());
      convertView = holder.view;
    } else {
      holder = (ViewHolder) convertView.getTag();
    }
    holder.setup(position);
    return convertView;
  }

  void selectNone() {
    selectedPosition = -1;
    notifyDataSetChanged();
  }

  interface OnColorSelectedListener {

    void onColorSelected(int color);
  }

  private final class ViewHolder {

    View view;
    ColorPanelView colorPanelView;
    ImageView imageView;
    int originalBorderColor;

    ViewHolder(Context context) {
      int layoutResId;
      if (colorShape == ColorShape.SQUARE) {
        layoutResId = R.layout.cpv_color_item_square;
      } else {
        layoutResId = R.layout.cpv_color_item_circle;
      }
      view = View.inflate(context, layoutResId, null);
      colorPanelView = (ColorPanelView) view.findViewById(R.id.cpv_color_panel_view);
      imageView = (ImageView) view.findViewById(R.id.cpv_color_image_view);
      originalBorderColor = colorPanelView.getBorderColor();
      view.setTag(this);
    }

    void setup(int position) {
      int color = colors[position];
      int alpha = Color.alpha(color);
      colorPanelView.setColor(color);
      imageView.setImageResource(selectedPosition == position ? R.drawable.cpv_preset_checked : 0);
      if (alpha != 255) {
        if (alpha <= ColorPickerDialog.ALPHA_THRESHOLD) {
          colorPanelView.setBorderColor(color | 0xFF000000);
          imageView.setColorFilter(/*color | 0xFF000000*/Color.BLACK, PorterDuff.Mode.SRC_IN);
        } else {
          colorPanelView.setBorderColor(originalBorderColor);
          imageView.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
      } else {
        setColorFilter(position);
      }
      setOnClickListener(position);
    }

    private void setOnClickListener(final int position) {
      colorPanelView.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          if (selectedPosition != position) {
            selectedPosition = position;
            notifyDataSetChanged();
          }
          listener.onColorSelected(colors[position]);
        }
      });
      colorPanelView.setOnLongClickListener(new View.OnLongClickListener() {
        @Override public boolean onLongClick(View v) {
          colorPanelView.showHint();
          return true;
        }
      });
    }

    private void setColorFilter(int position) {
      if (position == selectedPosition && ColorUtils.calculateLuminance(colors[position]) >= 0.65) {
        imageView.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
      } else {
        imageView.setColorFilter(null);
      }
    }
  }
}