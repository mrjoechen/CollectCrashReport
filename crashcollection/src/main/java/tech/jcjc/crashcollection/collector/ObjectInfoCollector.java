package tech.jcjc.crashcollection.collector;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tech.jcjc.crashcollection.interfaces.ICrashInterface;

public class ObjectInfoCollector extends ICrashCollector {
    private Context mContext;

    private static final String TAG = "[Object]";

    private PackageManager pm;

    private ICrashInterface crashInterface;

    @Override
    public void Init(Context context, ICrashInterface c) {
        mContext = context;
        crashInterface = c;
        pm = mContext.getPackageManager();
    }

    @Override
    public void runCollector(int type, Thread thread, Object ex, FilePrintWriter printWriter) {
        printWriter.setFile(SUMMARY_FILE);
        printWriter.println(TAG);
        try {
            PackageInfo packageInfo = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
            List<Object> dumpObj = new ArrayList<Object>();
            dumpObj.add(packageInfo.applicationInfo);
            printWriter.println(makeupKeyValue("Application", dumpObject(dumpObj)));

            Map<String, String> exts = crashInterface.objectInfoCrashCollector(type, thread, ex);
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

    private String dumpObject(List<Object> objList) {
        String dump = "";
        try {
            for (Object obj : objList) {
                Field[] fields = obj.getClass().getDeclaredFields();
                dump += (obj.getClass().getName() + "=\r\n");
                for (Field field : fields) {
                    try {
                        field.setAccessible(true);
                        dump += ("\t" + field.getName() + "=" + field.get(obj) + "\r\n");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dump;
    }
}
