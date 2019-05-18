package tech.jcjc.crashcollection.collector;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.SystemUtil;

public class BaseInfoCollector extends ICrashCollector {
    private Context mContext;

    private static final String TAG = "[INFO]";

    private long applicationBootTime;

    private ICrashInterface crashInterface;

    private String mImeiValue = "";

    public static final String CRASH_SHARE_PREF_NAME = "crash_config";

    private static final String MID = "MID";

    private static final String IMEI = "IMEI";

    private static final String PRODUCT_MODEL = "PRODUCT_MODEL";

    private static final String ROM = "ROM";
    
    // 收集基本信息的同时在本目录下生成一个名为<crashhash>.hash的文件，供上传时使用
    // 此文件不上传
    public static final String CRASH_HASH_TMP_FILE_SUF = ".hash";

    @Override
    public void Init(Context context, ICrashInterface c) {
        mContext = context;
        crashInterface = c;
        applicationBootTime = System.currentTimeMillis();
        // 很多时候崩溃时无法获取到imei，所以此处在进程初始化时，就检查一下，将imei写入SharePref中
        getImei(context);
    }


    @Override
    public void runCollector(int type, Thread thread, Object ex, FilePrintWriter printWriter) {
        long currentTime = System.currentTimeMillis();
        printWriter.setFile(SUMMARY_FILE);
        printWriter.println(TAG);
        String crashHash = null;
        try {
            crashHash = getCrashHash(type, ex);
            printWriter.println(makeupKeyValue("CRASH_HASH", crashHash));
//            printWriter.println(makeupKeyValue(MID, getMid(mContext)));
            printWriter.println(makeupKeyValue("DATE", new SimpleDateFormat("yyyy-M-dd HH:mm:ss", Locale.CHINA).format(new Date())));
            printWriter.println(makeupKeyValue("CRASH_TIME", String.valueOf(currentTime)));
            printWriter.println(makeupKeyValue("BOOT_TIME", String.valueOf(SystemClock.elapsedRealtime())));
            printWriter.println(makeupKeyValue("INIT_TIME", String.valueOf(currentTime - applicationBootTime)));
//            printWriter.println(makeupKeyValue(IMEI, getImei(mContext)));
            printWriter.println(makeupKeyValue("VERSION", crashInterface.getVersion()));
            printWriter.println(makeupKeyValue("PROCESS", getProcessName(mContext)));
            printWriter.println(makeupKeyValue(PRODUCT_MODEL, getCompanyModel()));
            printWriter.println(makeupKeyValue("RELEASE", Build.VERSION.SDK + "." + Build.VERSION.RELEASE));
//            printWriter.println(makeupKeyValue(ROM, getProductRom()));
            printWriter.println(makeupKeyValue("STATE", getCrashState(ex)));

            Map<String, String> exts = crashInterface.baseInfoCrashCollector(type, thread, ex);
            if (exts != null) {
                for (Map.Entry<String, String> entry : exts.entrySet()) {
                    printWriter.println(makeupKeyValue(entry.getKey(), entry.getValue()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        printWriter.println();
        
        if (crashHash != null) {
            printWriter.setFile(crashHash + CRASH_HASH_TMP_FILE_SUF);
        }
    }

    /**
     * 获取CrashHash，为了不改变接口，在这里增加对BreakPad类型的判断
     *
     * @param type 类型
     * @param ex   异常
     * @return CrashHash
     */
    private String getCrashHash(int type, Object ex) {
        try {
            if (type == CrashReportImpl.BREAKPAD_CAUGHT_CRASH_TYPE) {
                return "BREAKPAD000000000000000000000000";
            } else {
                return StackInfoCollector.getThrowableHashCode(ex);
            }
        } catch (Exception e) {
            //ignore
            return "111111111111111111111111111111111";
        }
    }

    /**
     * 获取当前包的状态
     *
     * @param ex 异常
     * @return debug包为2，release包为0
     */
    private String getCrashState(Object ex) {
        if (crashInterface.isDebugable()) {
            return "2";
        } else if (ex instanceof Throwable) {
            return "0";
        } else if (ex instanceof String) {
            return "1";
        } else {
            return "3";
        }
    }

    /**
     * 先从SharePref读取，失败后，从系统获取，若成功则写入SharePref
     *
     * @param c context
     * @return mid
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private String getMid(Context c) {
        try {
            String sharePrefMid = c.getSharedPreferences(CRASH_SHARE_PREF_NAME, Context.MODE_PRIVATE).getString(MID, "");
            if (TextUtils.isEmpty(sharePrefMid)) {
                String mid = SystemUtil.getMid2(c);
                if (!TextUtils.isEmpty(mid)) {
                    c.getSharedPreferences(CRASH_SHARE_PREF_NAME, Context.MODE_PRIVATE).edit().putString(MID, mid).apply();
                    return mid;
                } else {
                    return "0";
                }

            } else {
                return sharePrefMid;
            }
        } catch (Exception e) {
            return "0";
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private String getImei(Context c) {
        try {
            String imei = c.getSharedPreferences(CRASH_SHARE_PREF_NAME, Context.MODE_PRIVATE).getString(IMEI, "");
            //在有些机型上，如果用户不给权限，则会返回"0"
            if (TextUtils.isEmpty(imei) || imei.equals(SystemUtil.DEFAULT_IMEI) || imei.equals("0")) {
                imei = SystemUtil.getImei(c);
                if (!TextUtils.isEmpty(imei) && !imei.equals(SystemUtil.DEFAULT_IMEI) && !imei.equals("0")) {
                    c.getSharedPreferences(CRASH_SHARE_PREF_NAME, Context.MODE_PRIVATE).edit().putString(IMEI, imei).apply();
                }
                return imei;
            }

            return imei;
        } catch (Exception e) {
            return "0";
        }
    }

    private String getCompanyModel() {
        try {
            return URLEncoder.encode(Build.MANUFACTURER + "+" + Build.MODEL, "UTF-8");
        } catch (Throwable e) {
            // ignore
        }
        return "unknown";
    }

    private String getProductRom() {
        try {
            String rom = getFileProperties("/system/build.prop", "ro.build.description");
            if (!TextUtils.isEmpty(rom)) {
                return URLEncoder.encode(rom.split(" ")[0], "UTF-8");
            }
        } catch (Exception e) {
            // ignore
        }
        return "unknown";
    }
}
