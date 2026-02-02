package org.autojs.autojs.ui.common;

import android.content.Context;
import com.afollestad.materialdialogs.MaterialDialog;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Oct 21, 2017.
 * Modified by SuperMonster003 as of Feb 1, 2026.
 */
public class RxDialogs {

    public static Observable<Boolean> confirm(Context context, String content) {
        return confirm(context, content, 0);
    }

    public static Observable<Boolean> confirm(Context context, String content, int positiveColorRes) {
        PublishSubject<Boolean> subject = PublishSubject.create();
        MaterialDialog.Builder builder = new MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(content)
                .negativeText(R.string.dialog_button_abandon)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> {
                    subject.onNext(false);
                    subject.onComplete();
                })
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_default)
                .onPositive((dialog, which) -> {
                    subject.onNext(true);
                    subject.onComplete();
                });
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
