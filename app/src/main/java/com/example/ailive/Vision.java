package com.example.ailive;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class Vision {
    private Context mContext;

    // Constructor that accepts a context
    public Vision(Context context) {
        this.mContext = context;
    }

    public void openURLInChrome() {
        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", "详细分析这张图片，说中文");
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.openai.com/"));
        intent.setPackage("com.android.chrome");

        // Check if the Chrome browser is installed
        mContext.startActivity(intent);
    }
}
