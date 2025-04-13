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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * <p>A dialog to pick a color.</p>
 *
 * <p>The {@link Activity activity} that shows this dialog should implement {@link ColorPickerDialogListener}</p>
 *
 * <p>Example usage:</p>
 *
 * <pre>
 *   ColorPickerDialog.newBuilder().show(activity);
 * </pre>
 *
 * <p>Modified by SuperMonster003 as of Mar 25, 2025.</p>
 */
public class ColorPickerDialog extends DialogFragment implements ColorPickerView.OnColorChangedListener, TextWatcher {

    private static final String TAG = "ColorPickerDialog";

    public static final int TYPE_CUSTOM = 0;
    public static final int TYPE_PRESETS = 1;

    /**
     * Material design colors used as the default color presets
     */
    public static final int[] MATERIAL_COLORS = {
            0xFFF44336, // RED 500
            0xFFE91E63, // PINK 500
            0xFFFF2C93, // LIGHT PINK 500
            0xFF9C27B0, // PURPLE 500
            0xFF673AB7, // DEEP PURPLE 500
            0xFF3F51B5, // INDIGO 500
            0xFF2196F3, // BLUE 500
            0xFF03A9F4, // LIGHT BLUE 500
            0xFF00BCD4, // CYAN 500
            0xFF009688, // TEAL 500
            0xFF4CAF50, // GREEN 500
            0xFF8BC34A, // LIGHT GREEN 500
            0xFFCDDC39, // LIME 500
            0xFFFFEB3B, // YELLOW 500
            0xFFFFC107, // AMBER 500
            0xFFFF9800, // ORANGE 500
            0xFF795548, // BROWN 500
            0xFF607D8B, // BLUE GREY 500
            0xFF9E9E9E, // GREY 500
    };

    static final int ALPHA_THRESHOLD = 165;

    private static final String ARG_ID = "id";
    private static final String ARG_TYPE = "dialogType";
    private static final String ARG_COLOR = "color";
    private static final String ARG_ALPHA = "alpha";
    private static final String ARG_PRESETS = "presets";
    private static final String ARG_ALLOW_PRESETS = "allowPresets";
    private static final String ARG_ALLOW_CUSTOM = "allowCustom";
    private static final String ARG_DIALOG_TITLE = "dialogTitle";
    private static final String ARG_SHOW_COLOR_SHADES = "showColorShades";
    private static final String ARG_COLOR_SHAPE = "colorShape";
    private static final String ARG_PRESETS_BUTTON_TEXT = "presetsButtonText";
    private static final String ARG_CUSTOM_BUTTON_TEXT = "customButtonText";
    private static final String ARG_SELECTED_BUTTON_TEXT = "selectedButtonText";
    private static final String ARG_USE_LEGACY_MODE = "useLegacyMode";

    ColorPickerDialogListener colorPickerDialogListener;
    View.OnClickListener oldColorPanelOnClickListener;
    View.OnClickListener newColorPanelOnClickListener;
    Consumer<ColorPickerDialog> colorHistoriesHandler;

    FrameLayout rootView;
    ImageView customTitleOptions;
    int[] presets;
    @ColorInt
    int color;
    int dialogType;
    int dialogId;
    boolean showColorShades;
    int colorShape;
    boolean useLegacyMode;

    // -- PRESETS --------------------------
    ColorPaletteAdapter adapter;
    LinearLayout shadesLayout;
    SeekBar transparencySeekBar;
    TextView transparencyPercText;

    // -- CUSTOM ---------------------------
    ColorPickerView colorPicker;
    ColorPanelView oldColorPanel;
    ColorPanelView newColorPanel;
    EditText hexEditText;
    boolean showAlphaSlider;
    private int presetsButtonStringRes;
    private boolean fromEditText;
    private boolean fromOldColorPanel;
    private int customButtonStringRes;

    private final OnTouchListener onPickerTouchListener = new OnTouchListener() {
        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouch(View v, MotionEvent event) {
            if (v != hexEditText && hexEditText.hasFocus()) {
                hexEditText.clearFocus();
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(hexEditText.getWindowToken(), 0);
                hexEditText.clearFocus();
                return true;
            }
            return false;
        }
    };

    /**
     * Create a new Builder for creating a {@link ColorPickerDialog} instance
     *
     * @return The {@link Builder builder} to create the {@link ColorPickerDialog}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    @NotNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        dialogId = requireArguments().getInt(ARG_ID);
        showAlphaSlider = requireArguments().getBoolean(ARG_ALPHA);
        showColorShades = requireArguments().getBoolean(ARG_SHOW_COLOR_SHADES);
        colorShape = requireArguments().getInt(ARG_COLOR_SHAPE);
        useLegacyMode = requireArguments().getBoolean(ARG_USE_LEGACY_MODE, false);
        if (savedInstanceState == null) {
            color = requireArguments().getInt(ARG_COLOR);
            dialogType = requireArguments().getInt(ARG_TYPE);
        } else {
            color = savedInstanceState.getInt(ARG_COLOR);
            dialogType = savedInstanceState.getInt(ARG_TYPE);
        }

        rootView = new FrameLayout(requireActivity());
        if (dialogType == TYPE_CUSTOM) {
            rootView.addView(createPickerView());
        } else if (dialogType == TYPE_PRESETS) {
            rootView.addView(createPresetsView());
        }

        int selectedButtonStringRes = requireArguments().getInt(ARG_SELECTED_BUTTON_TEXT);
        if (selectedButtonStringRes == 0) {
            selectedButtonStringRes = R.string.cpv_select;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity()).setView(rootView)
                .setPositiveButton(selectedButtonStringRes, (dialog, which) -> onColorSelected(color));

        View customTitleRoot = View.inflate(requireContext(), R.layout.cpv_dialog_custom_title, null);
        builder.setCustomTitle(customTitleRoot);

        customTitleOptions = customTitleRoot.findViewById(R.id.more_options);

        int dialogTitleStringRes = requireArguments().getInt(ARG_DIALOG_TITLE);
        if (dialogTitleStringRes != 0) {
            // builder.setTitle(dialogTitleStringRes);
            TextView customTitleTitle = customTitleRoot.findViewById(R.id.title);
            customTitleTitle.setText(dialogTitleStringRes);
        }

        if (useLegacyMode) {
            customTitleOptions.setVisibility(View.GONE);
        } else {
            customTitleOptions.setOnClickListener(v -> {
                var popupMenu = new PopupMenu(requireContext(), v, Gravity.END);
                popupMenu.getMenuInflater().inflate(R.menu.cpv_dialog_more_options, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == R.id.action_reset_color_palette) {
                        resetColorPanel();
                        return true;
                    }
                    if (menuItem.getItemId() == R.id.action_show_histories) {
                        showHistories(this);
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
            });
        }

        presetsButtonStringRes = requireArguments().getInt(ARG_PRESETS_BUTTON_TEXT);
        customButtonStringRes = requireArguments().getInt(ARG_CUSTOM_BUTTON_TEXT);

        int neutralButtonStringRes;
        if (dialogType == TYPE_CUSTOM && requireArguments().getBoolean(ARG_ALLOW_PRESETS)) {
            neutralButtonStringRes = (presetsButtonStringRes != 0 ? presetsButtonStringRes : R.string.cpv_presets);
        } else if (dialogType == TYPE_PRESETS && requireArguments().getBoolean(ARG_ALLOW_CUSTOM)) {
            neutralButtonStringRes = (customButtonStringRes != 0 ? customButtonStringRes : R.string.cpv_custom);
        } else {
            neutralButtonStringRes = 0;
        }

        if (neutralButtonStringRes != 0) {
            builder.setNeutralButton(neutralButtonStringRes, null);
        }

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();

        // http://stackoverflow.com/a/16972670/1048340
        // noinspection ConstantConditions
        dialog.getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        // Do not dismiss the dialog when clicking the neutral button.
        Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutralButton != null) {
            neutralButton.setOnClickListener(v -> {
                rootView.removeAllViews();
                switch (dialogType) {
                    case TYPE_CUSTOM:
                        dialogType = TYPE_PRESETS;
                        if (!useLegacyMode && customTitleOptions != null) {
                            customTitleOptions.setVisibility(View.GONE);
                        }
                        ((Button) v).setText(customButtonStringRes != 0 ? customButtonStringRes : R.string.cpv_custom);
                        rootView.addView(createPresetsView());
                        break;
                    case TYPE_PRESETS:
                        dialogType = TYPE_CUSTOM;
                        if (!useLegacyMode && customTitleOptions != null) {
                            customTitleOptions.setVisibility(View.VISIBLE);
                        }
                        ((Button) v).setText(presetsButtonStringRes != 0 ? presetsButtonStringRes : R.string.cpv_presets);
                        rootView.addView(createPickerView());
                }
            });
        }
    }

    @Override
    public void onDismiss(@NotNull DialogInterface dialog) {
        super.onDismiss(dialog);
        onDialogDismissed();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(ARG_COLOR, color);
        outState.putInt(ARG_TYPE, dialogType);
        super.onSaveInstanceState(outState);
    }

    /**
     * Set the callback.
     * <p/>
     * Note: The preferred way to handle the callback is to have the calling Activity implement
     * {@link ColorPickerDialogListener} as this will not survive an orientation change.
     *
     * @param colorPickerDialogListener The callback invoked when a color is selected or the dialog is dismissed.
     */
    public ColorPickerDialog setColorPickerDialogListener(ColorPickerDialogListener colorPickerDialogListener) {
        this.colorPickerDialogListener = colorPickerDialogListener;
        return this;
    }

    // region Custom Picker

    View createPickerView() {
        View contentView = View.inflate(getActivity(), R.layout.cpv_dialog_color_picker, null);
        colorPicker = contentView.findViewById(R.id.cpv_color_picker_view);
        oldColorPanel = contentView.findViewById(R.id.cpv_color_panel_old);
        newColorPanel = contentView.findViewById(R.id.cpv_color_panel_new);
        ImageView arrowRight = contentView.findViewById(R.id.cpv_arrow_right);
        hexEditText = contentView.findViewById(R.id.cpv_hex);

        try {
            final TypedValue value = new TypedValue();
            TypedArray typedArray = requireActivity().obtainStyledAttributes(value.data, new int[]{android.R.attr.textColorPrimary});
            int arrowColor = typedArray.getColor(0, Color.BLACK);
            typedArray.recycle();
            arrowRight.setColorFilter(arrowColor);
        } catch (Exception ignored) {
            /* Ignored. */
        }

        colorPicker.setAlphaSliderVisible(showAlphaSlider);
        oldColorPanel.setColor(requireArguments().getInt(ARG_COLOR));
        colorPicker.setColor(color, true);
        colorPicker.lastColor = color;
        newColorPanel.setColor(color);
        setHex(color);

        if (!showAlphaSlider) {
            hexEditText.setFilters(new InputFilter[]{new HexMaxLengthInputFilter(6)});
        }

        if (!useLegacyMode) {
            oldColorPanel.setOnClickListener(v -> {
                v.setTag(oldColorPanel.getColor());
                oldColorPanelOnClickListener.onClick(v);
            });
            oldColorPanel.setOnLongClickListener(v -> {
                resetColorPanel();
                return true;
            });
            newColorPanel.setOnClickListener(v -> {
                v.setTag(newColorPanel.getColor());
                newColorPanelOnClickListener.onClick(v);
            });
            newColorPanel.setOnLongClickListener(v -> {
                int aimColor = colorPicker.lastColor;
                newColorPanel.setColor(aimColor);
                colorPicker.setColor(aimColor, true);
                return true;
            });
        } else {
            newColorPanel.setOnClickListener(v -> {
                if (newColorPanel.getColor() == color) {
                    onColorSelected(color);
                    dismiss();
                }
            });
        }

        contentView.setOnTouchListener(onPickerTouchListener);
        colorPicker.setOnColorChangedListener(this);
        hexEditText.addTextChangedListener(this);

        hexEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(hexEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        return contentView;
    }

    private void resetColorPanel() {
        if (oldColorPanel == null) return;
        int aimColor = oldColorPanel.getColor();
        fromOldColorPanel = true;
        newColorPanel.setColor(aimColor);
        colorPicker.setColor(aimColor, true);
    }

    private void showHistories(ColorPickerDialog colorPickerDialog) {
        if (colorHistoriesHandler == null) return;
        colorHistoriesHandler.accept(colorPickerDialog);
    }

    public void onHistorySelected(@ColorInt int color) {
        colorPicker.setColor(color, true);
        colorPicker.lastColor = color;
    }

    @Override
    public void onColorChanged(int newColor) {
        color = newColor;
        if (!fromOldColorPanel) {
            colorPicker.lastColor = newColor;
        }
        if (newColorPanel != null) {
            newColorPanel.setColor(newColor);
        }
        if (!fromEditText && hexEditText != null) {
            setHex(newColor);
            if (hexEditText.hasFocus()) {
                InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(hexEditText.getWindowToken(), 0);
                hexEditText.clearFocus();
            }
        }
        fromEditText = false;
        fromOldColorPanel = false;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (hexEditText.isFocused()) {
            int color = parseColorString(s.toString());
            if (color != colorPicker.getColor()) {
                fromEditText = true;
                colorPicker.setColor(color, true);
                colorPicker.lastColor = color;
            }
        }
    }

    private void setHex(int color) {
        if (showAlphaSlider) {
            hexEditText.setText(String.format("%08X", (color)));
        } else {
            hexEditText.setText(String.format("%06X", (0xFFFFFF & color)));
        }
    }

    private int parseColorString(String colorString) throws NumberFormatException {
        int a, r, g, b = 0;
        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1);
        }
        if (colorString.isEmpty()) {
            r = 0;
            a = 255;
            g = 0;
        } else if (colorString.length() <= 2) {
            a = 255;
            r = 0;
            b = Integer.parseInt(colorString, 16);
            g = 0;
        } else if (colorString.length() == 3) {
            a = 255;
            r = Integer.parseInt(colorString.substring(0, 1), 16);
            g = Integer.parseInt(colorString.substring(1, 2), 16);
            b = Integer.parseInt(colorString.substring(2, 3), 16);
        } else if (colorString.length() == 4) {
            a = 255;
            r = Integer.parseInt(colorString.substring(0, 2), 16);
            g = r;
            r = 0;
            b = Integer.parseInt(colorString.substring(2, 4), 16);
        } else if (colorString.length() == 5) {
            a = 255;
            r = Integer.parseInt(colorString.substring(0, 1), 16);
            g = Integer.parseInt(colorString.substring(1, 3), 16);
            b = Integer.parseInt(colorString.substring(3, 5), 16);
        } else if (colorString.length() == 6) {
            a = 255;
            r = Integer.parseInt(colorString.substring(0, 2), 16);
            g = Integer.parseInt(colorString.substring(2, 4), 16);
            b = Integer.parseInt(colorString.substring(4, 6), 16);
        } else if (colorString.length() == 7) {
            a = Integer.parseInt(colorString.substring(0, 1), 16);
            r = Integer.parseInt(colorString.substring(1, 3), 16);
            g = Integer.parseInt(colorString.substring(3, 5), 16);
            b = Integer.parseInt(colorString.substring(5, 7), 16);
        } else if (colorString.length() == 8) {
            a = Integer.parseInt(colorString.substring(0, 2), 16);
            r = Integer.parseInt(colorString.substring(2, 4), 16);
            g = Integer.parseInt(colorString.substring(4, 6), 16);
            b = Integer.parseInt(colorString.substring(6, 8), 16);
        } else {
            b = -1;
            g = -1;
            r = -1;
            a = -1;
        }
        return Color.argb(a, r, g, b);
    }

    // -- endregion --

    // region Presets Picker

    View createPresetsView() {
        View contentView = View.inflate(getActivity(), R.layout.cpv_dialog_presets, null);
        shadesLayout = contentView.findViewById(R.id.shades_layout);
        transparencySeekBar = contentView.findViewById(R.id.transparency_seekbar);
        transparencyPercText = contentView.findViewById(R.id.transparency_text);
        GridView gridView = contentView.findViewById(R.id.gridView);

        loadPresets();

        if (showColorShades) {
            createColorShades(color);
        } else {
            shadesLayout.setVisibility(View.GONE);
            contentView.findViewById(R.id.shades_divider).setVisibility(View.GONE);
        }

        adapter = new ColorPaletteAdapter(newColor -> {
            if (color == newColor) {
                // Double tab selects the color
                ColorPickerDialog.this.onColorSelected(color);
                dismiss();
                return;
            }
            color = newColor;
            if (showColorShades) {
                createColorShades(color);
            }
        }, presets, getSelectedItemPosition(), colorShape);

        gridView.setAdapter(adapter);

        if (showAlphaSlider) {
            setupTransparency();
        } else {
            contentView.findViewById(R.id.transparency_layout).setVisibility(View.GONE);
            contentView.findViewById(R.id.transparency_title).setVisibility(View.GONE);
        }

        return contentView;
    }

    private void loadPresets() {
        int alpha = Color.alpha(color);
        presets = requireArguments().getIntArray(ARG_PRESETS);
        if (presets == null) presets = MATERIAL_COLORS;
        boolean isMaterialColors = presets == MATERIAL_COLORS;
        presets = Arrays.copyOf(presets, presets.length); // don't update the original array when modifying alpha
        if (alpha != 255) {
            // add alpha to the presets
            for (int i = 0; i < presets.length; i++) {
                int color = presets[i];
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                presets[i] = Color.argb(alpha, red, green, blue);
            }
        }
        presets = unshiftIfNotExists(presets, color);
        int initialColor = requireArguments().getInt(ARG_COLOR);
        if (initialColor != color) {
            // The user clicked a color and a configuration change occurred. Make sure the initial color is in the presets
            presets = unshiftIfNotExists(presets, initialColor);
        }
        if (isMaterialColors && presets.length == 19) {
            // Add black to have a total of 20 colors if the current color is in the material color palette
            presets = pushIfNotExists(presets, Color.argb(alpha, 0, 0, 0));
        }
    }

    void createColorShades(@ColorInt final int color) {
        final int[] colorShades = getColorShades(color);

        if (shadesLayout.getChildCount() != 0) {
            for (int i = 0; i < shadesLayout.getChildCount(); i++) {
                FrameLayout layout = (FrameLayout) shadesLayout.getChildAt(i);
                final ColorPanelView cpv = layout.findViewById(R.id.cpv_color_panel_view);
                ImageView iv = layout.findViewById(R.id.cpv_color_image_view);
                cpv.setColor(colorShades[i]);
                cpv.setTag(false);
                iv.setImageDrawable(null);
            }
            return;
        }

        final int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.cpv_item_horizontal_padding);

        for (final int colorShade : colorShades) {
            int layoutResId;
            if (colorShape == ColorShape.SQUARE) {
                layoutResId = R.layout.cpv_color_item_square;
            } else {
                layoutResId = R.layout.cpv_color_item_circle;
            }

            final View view = View.inflate(getActivity(), layoutResId, null);
            final ColorPanelView colorPanelView = view.findViewById(R.id.cpv_color_panel_view);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) colorPanelView.getLayoutParams();
            params.leftMargin = params.rightMargin = horizontalPadding;
            colorPanelView.setLayoutParams(params);
            colorPanelView.setColor(colorShade);
            shadesLayout.addView(view);

            colorPanelView.post(() -> {
                // The color is black when rotating the dialog. This is a dirty fix. WTF!?
                colorPanelView.setColor(colorShade);
            });

            colorPanelView.setOnClickListener(v -> {
                if (v.getTag() instanceof Boolean && (Boolean) v.getTag()) {
                    onColorSelected(ColorPickerDialog.this.color);
                    dismiss();
                    return; // already selected
                }
                ColorPickerDialog.this.color = colorPanelView.getColor();
                adapter.selectNone();
                for (int i = 0; i < shadesLayout.getChildCount(); i++) {
                    FrameLayout layout = (FrameLayout) shadesLayout.getChildAt(i);
                    ColorPanelView cpv = layout.findViewById(R.id.cpv_color_panel_view);
                    ImageView iv = layout.findViewById(R.id.cpv_color_image_view);
                    iv.setImageResource(cpv == v ? R.drawable.cpv_preset_checked : 0);
                    if (cpv == v && ColorUtils.calculateLuminance(cpv.getColor()) >= 0.65
                        || Color.alpha(cpv.getColor()) <= ALPHA_THRESHOLD) {
                        iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                    } else {
                        iv.setColorFilter(null);
                    }
                    cpv.setTag(cpv == v);
                }
            });
            colorPanelView.setOnLongClickListener(v -> {
                colorPanelView.showHint();
                return true;
            });
        }
    }

    private void onColorSelected(int color) {
        if (colorPickerDialogListener != null) {
            Log.w(TAG, "Using deprecated listener which may be remove in future releases");
            colorPickerDialogListener.onColorSelected(dialogId, color);
            return;
        }
        Activity activity = getActivity();
        if (activity instanceof ColorPickerDialogListener) {
            ((ColorPickerDialogListener) activity).onColorSelected(dialogId, color);
        } else {
            throw new IllegalStateException("The activity must implement ColorPickerDialogListener");
        }
    }

    private void onDialogDismissed() {
        if (colorPickerDialogListener != null) {
            Log.w(TAG, "Using deprecated listener which may be remove in future releases");
            colorPickerDialogListener.onDialogDismissed(dialogId);
            return;
        }
        Activity activity = getActivity();
        if (activity instanceof ColorPickerDialogListener) {
            ((ColorPickerDialogListener) activity).onDialogDismissed(dialogId);
        }
    }

    private int shadeColor(@ColorInt int color, double percent) {
        String hex = String.format("#%06X", (0xFFFFFF & color));
        long f = Long.parseLong(hex.substring(1), 16);
        double t = percent < 0 ? 0 : 255;
        double p = percent < 0 ? percent * -1 : percent;
        long R = f >> 16;
        long G = f >> 8 & 0x00FF;
        long B = f & 0x0000FF;
        int alpha = Color.alpha(color);
        int red = (int) (Math.round((t - R) * p) + R);
        int green = (int) (Math.round((t - G) * p) + G);
        int blue = (int) (Math.round((t - B) * p) + B);
        return Color.argb(alpha, red, green, blue);
    }

    private int[] getColorShades(@ColorInt int color) {
        return new int[]{
                shadeColor(color, 0.9), shadeColor(color, 0.7), shadeColor(color, 0.5), shadeColor(color, 0.333),
                shadeColor(color, 0.166), shadeColor(color, -0.125), shadeColor(color, -0.25), shadeColor(color, -0.375),
                shadeColor(color, -0.5), shadeColor(color, -0.675), shadeColor(color, -0.7), shadeColor(color, -0.775),
        };
    }

    private void setupTransparency() {
        int progress = 255 - Color.alpha(color);
        transparencySeekBar.setMax(255);
        transparencySeekBar.setProgress(progress);
        int percentage = (int) ((double) progress * 100 / 255);
        transparencyPercText.setText(String.format(Locale.ENGLISH, "%d%%", percentage));
        transparencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percentage = (int) ((double) progress * 100 / 255);
                transparencyPercText.setText(String.format(Locale.ENGLISH, "%d%%", percentage));
                int alpha = 255 - progress;
                // update items in GridView:
                for (int i = 0; i < adapter.colors.length; i++) {
                    int color = adapter.colors[i];
                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);
                    adapter.colors[i] = Color.argb(alpha, red, green, blue);
                }
                adapter.notifyDataSetChanged();
                // update shades:
                for (int i = 0; i < shadesLayout.getChildCount(); i++) {
                    FrameLayout layout = (FrameLayout) shadesLayout.getChildAt(i);
                    ColorPanelView cpv = layout.findViewById(R.id.cpv_color_panel_view);
                    ImageView iv = layout.findViewById(R.id.cpv_color_image_view);
                    if (layout.getTag() == null) {
                        // save the original border color
                        layout.setTag(cpv.getBorderColor());
                    }
                    int color = cpv.getColor();
                    color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
                    if (alpha <= ALPHA_THRESHOLD) {
                        cpv.setBorderColor(color | 0xFF000000);
                    } else {
                        cpv.setBorderColor((int) layout.getTag());
                    }
                    if (cpv.getTag() != null && (Boolean) cpv.getTag()) {
                        // The alpha changed on the selected shaded color. Update the checkmark color filter.
                        if (alpha <= ALPHA_THRESHOLD) {
                            iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                        } else {
                            if (ColorUtils.calculateLuminance(color) >= 0.65) {
                                iv.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
                            } else {
                                iv.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                            }
                        }
                    }
                    cpv.setColor(color);
                }
                // update color:
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                color = Color.argb(alpha, red, green, blue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private int[] unshiftIfNotExists(int[] array, int value) {
        boolean present = false;
        for (int i : array) {
            if (i == value) {
                present = true;
                break;
            }
        }
        if (!present) {
            int[] newArray = new int[array.length + 1];
            newArray[0] = value;
            System.arraycopy(array, 0, newArray, 1, newArray.length - 1);
            return newArray;
        }
        return array;
    }

    private int[] pushIfNotExists(int[] array, int value) {
        boolean present = false;
        for (int i : array) {
            if (i == value) {
                present = true;
                break;
            }
        }
        if (!present) {
            int[] newArray = new int[array.length + 1];
            newArray[newArray.length - 1] = value;
            System.arraycopy(array, 0, newArray, 0, newArray.length - 1);
            return newArray;
        }
        return array;
    }

    private int getSelectedItemPosition() {
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == color) {
                return i;
            }
        }
        return -1;
    }

    // endregion

    // region Builder

    @IntDef({TYPE_CUSTOM, TYPE_PRESETS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DialogType {

    }

    /** @noinspection unused */
    public static final class Builder {

        @StringRes
        int dialogTitle = R.string.cpv_default_title;
        @StringRes
        int presetsButtonText = R.string.cpv_presets;
        @StringRes
        int customButtonText = R.string.cpv_custom;
        @StringRes
        int selectedButtonText = R.string.cpv_select;
        @DialogType
        int dialogType = TYPE_PRESETS;
        int[] presets = MATERIAL_COLORS;
        @ColorInt
        int color = Color.BLACK;
        int dialogId = 0;
        boolean showAlphaSlider = false;
        boolean allowPresets = true;
        boolean allowCustom = true;
        boolean showColorShades = true;
        @ColorShape
        int colorShape = ColorShape.CIRCLE;
        boolean useLegacyMode = false;

        View.OnClickListener oldColorPanelOnClickListener;
        View.OnClickListener newColorPanelOnClickListener;
        Consumer<ColorPickerDialog> colorHistoriesHandler;

        /*package*/ Builder() {

        }

        /**
         * Set the dialog title string resource id
         *
         * @param dialogTitle The string resource used for the dialog title
         * @return This builder object for chaining method calls
         */
        public Builder setDialogTitle(@StringRes int dialogTitle) {
            this.dialogTitle = dialogTitle;
            return this;
        }

        /**
         * Set the selected button text string resource id
         *
         * @param selectedButtonText The string resource used for the selected button text
         * @return This builder object for chaining method calls
         */
        public Builder setSelectedButtonText(@StringRes int selectedButtonText) {
            this.selectedButtonText = selectedButtonText;
            return this;
        }

        /**
         * Set the presets button text string resource id
         *
         * @param presetsButtonText The string resource used for the presets button text
         * @return This builder object for chaining method calls
         */
        public Builder setPresetsButtonText(@StringRes int presetsButtonText) {
            this.presetsButtonText = presetsButtonText;
            return this;
        }

        /**
         * Set the custom button text string resource id
         *
         * @param customButtonText The string resource used for the custom button text
         * @return This builder object for chaining method calls
         */
        public Builder setCustomButtonText(@StringRes int customButtonText) {
            this.customButtonText = customButtonText;
            return this;
        }

        /**
         * Set which dialog view to show.
         *
         * @param dialogType Either {@link ColorPickerDialog#TYPE_CUSTOM} or {@link ColorPickerDialog#TYPE_PRESETS}.
         * @return This builder object for chaining method calls
         */
        public Builder setDialogType(@DialogType int dialogType) {
            this.dialogType = dialogType;
            return this;
        }

        /**
         * Set the colors used for the presets
         *
         * @param presets An array of color ints.
         * @return This builder object for chaining method calls
         */
        public Builder setPresets(@NonNull int[] presets) {
            this.presets = presets;
            return this;
        }

        /**
         * Set the original color
         *
         * @param color The default color for the color picker
         * @return This builder object for chaining method calls
         */
        public Builder setColor(int color) {
            this.color = color;
            return this;
        }

        /**
         * Set the dialog id used for callbacks
         *
         * @param dialogId The id that is sent back to the {@link ColorPickerDialogListener}.
         * @return This builder object for chaining method calls
         */
        public Builder setDialogId(int dialogId) {
            this.dialogId = dialogId;
            return this;
        }

        /**
         * Show the alpha slider
         *
         * @param showAlphaSlider {@code true} to show the alpha slider. Currently only supported with the {@link
         *                        ColorPickerView}.
         * @return This builder object for chaining method calls
         */
        public Builder setShowAlphaSlider(boolean showAlphaSlider) {
            this.showAlphaSlider = showAlphaSlider;
            return this;
        }

        /**
         * Show/Hide a neutral button to select preset colors.
         *
         * @param allowPresets {@code false} to disable showing the presets button.
         * @return This builder object for chaining method calls
         */
        public Builder setAllowPresets(boolean allowPresets) {
            this.allowPresets = allowPresets;
            return this;
        }

        /**
         * Show/Hide the neutral button to select a custom color.
         *
         * @param allowCustom {@code false} to disable showing the custom button.
         * @return This builder object for chaining method calls
         */
        public Builder setAllowCustom(boolean allowCustom) {
            this.allowCustom = allowCustom;
            return this;
        }

        /**
         * Show/Hide the color shades in the presets picker
         *
         * @param showColorShades {@code false} to hide the color shades.
         * @return This builder object for chaining method calls
         */
        public Builder setShowColorShades(boolean showColorShades) {
            this.showColorShades = showColorShades;
            return this;
        }

        /**
         * Set the shape of the color panel view.
         *
         * @param colorShape Either {@link ColorShape#CIRCLE} or {@link ColorShape#SQUARE}.
         * @return This builder object for chaining method calls
         */
        public Builder setColorShape(int colorShape) {
            this.colorShape = colorShape;
            return this;
        }

        public Builder setUseLegacyMode(boolean legacyMode) {
            this.useLegacyMode = legacyMode;
            return this;
        }

        public Builder setOldColorPanelOnClickListener(View.OnClickListener onClickListener) {
            this.oldColorPanelOnClickListener = onClickListener;
            return this;
        }

        public Builder setNewColorPanelOnClickListener(View.OnClickListener onClickListener) {
            this.newColorPanelOnClickListener = onClickListener;
            return this;
        }

        public Builder setColorHistoriesHandler(Consumer<ColorPickerDialog> colorHistoriesHandler) {
            this.colorHistoriesHandler = colorHistoriesHandler;
            return this;
        }

        /**
         * Create the {@link ColorPickerDialog} instance.
         *
         * @return A new {@link ColorPickerDialog}.
         * @see #show(FragmentActivity)
         */
        public ColorPickerDialog create() {
            ColorPickerDialog dialog = new ColorPickerDialog();
            Bundle args = new Bundle();
            args.putInt(ARG_ID, dialogId);
            args.putInt(ARG_TYPE, dialogType);
            args.putInt(ARG_COLOR, color);
            args.putIntArray(ARG_PRESETS, presets);
            args.putBoolean(ARG_ALPHA, showAlphaSlider);
            args.putBoolean(ARG_ALLOW_CUSTOM, allowCustom);
            args.putBoolean(ARG_ALLOW_PRESETS, allowPresets);
            args.putInt(ARG_DIALOG_TITLE, dialogTitle);
            args.putBoolean(ARG_SHOW_COLOR_SHADES, showColorShades);
            args.putInt(ARG_COLOR_SHAPE, colorShape);
            args.putInt(ARG_PRESETS_BUTTON_TEXT, presetsButtonText);
            args.putInt(ARG_CUSTOM_BUTTON_TEXT, customButtonText);
            args.putInt(ARG_SELECTED_BUTTON_TEXT, selectedButtonText);
            args.putBoolean(ARG_USE_LEGACY_MODE, useLegacyMode);
            dialog.oldColorPanelOnClickListener = oldColorPanelOnClickListener;
            dialog.newColorPanelOnClickListener = newColorPanelOnClickListener;
            dialog.colorHistoriesHandler = colorHistoriesHandler;
            dialog.setArguments(args);
            return dialog;
        }

        /**
         * Create and show the {@link ColorPickerDialog} created with this builder.
         *
         * @param activity The current activity.
         */
        public void show(FragmentActivity activity) {
            create().show(activity.getSupportFragmentManager(), "color-picker-dialog");
        }
    }

    // endregion
}
