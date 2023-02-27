package cn.bavelee.mirefreshrate;

import android.app.Activity;
import android.app.ProgressDialog;

public class LoadingDialog {

    private static ProgressDialog dialog;

    public static void show(Activity activity, String message) {
        dismiss();
        dialog = new ProgressDialog(activity);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.show();
    }

    public static void dismiss() {
        if (dialog != null) {
            dialog.cancel();
            dialog = null;
        }
    }
}
