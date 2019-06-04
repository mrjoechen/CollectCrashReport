
package tech.jcjc.crashcollection.collector;


import android.content.Context;

import java.util.Map;

import tech.jcjc.crashcollection.interfaces.ICrashInterface;


public class CustomInfoCollector extends ICrashCollector {
    private Context mContext;

    private ICrashInterface crashInterface
            ;

    private static final String TAG = "[State]";

    @Override
    public void Init(Context context, ICrashInterface c) {
        mContext = context;
        crashInterface = c;
    }

    @Override
    public void runCollector(int type, Thread thread, Object ex, FilePrintWriter printWriter) {
        printWriter.setFile(SUMMARY_FILE);
        printWriter.println(TAG);

        try {
            Map<String, String> exts = crashInterface.customInfoCrashCollector(type, thread, ex);
            if (exts != null) {
                for (Map.Entry<String, String> entry : exts.entrySet()) {
                    printWriter.println(makeupKeyValue(entry.getKey(), entry.getValue()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        printWriter.println();
    }

}
