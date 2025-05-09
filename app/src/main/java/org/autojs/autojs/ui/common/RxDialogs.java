package org.autojs.autojs.ui.common;

import android.content.Context;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs6.R;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by Stardust on Oct 21, 2017.
 */
public class RxDialogs {

    public static Observable<Boolean> confirm(Context context, String content) {
        return confirm(context, content, 0);
    }
    
    public static Observable<Boolean> confirm(Context context, String content, int positiveColorRes) {
        PublishSubject<Boolean> subject = PublishSubject.create();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(context)
                .content(content)
                .negativeText(R.string.text_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> subject.onNext(false))
                .positiveText(R.string.text_ok)
                .onPositive((dialog, which) -> subject.onNext(true));
        if (positiveColorRes != 0) {
            builder.positiveColorRes(positiveColorRes);
        }
        builder.show();
        return subject;
    }

    public static Observable<Boolean> confirm(Context context, int contentRes) {
        return confirm(context, context.getString(contentRes));
    }

    public static Observable<Boolean> confirm(Context context, int contentRes, int positiveColorRes) {
        return confirm(context, context.getString(contentRes), positiveColorRes);
    }

}
