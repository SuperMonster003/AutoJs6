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
import android.util.DisplayMetrics;
import android.util.TypedValue;

final class DrawingUtils {

  static int dpToPx(Context c, float dipValue) {
    DisplayMetrics metrics = c.getResources().getDisplayMetrics();
    float val = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    int res = (int) (val + 0.5); // Round
    // Ensure at least 1 pixel if val was > 0
    return res == 0 && val > 0 ? 1 : res;
  }
}
