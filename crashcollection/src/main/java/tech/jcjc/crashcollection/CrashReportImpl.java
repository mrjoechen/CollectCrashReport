
package tech.jcjc.crashcollection;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import tech.jcjc.crashcollection.collector.BaseInfoCollector;
import tech.jcjc.crashcollection.collector.ContextInfoCollector;
import tech.jcjc.crashcollection.collector.CustomInfoCollector;
import tech.jcjc.crashcollection.collector.ICrashCollector;
import tech.jcjc.crashcollection.collector.MemoryInfoCollector;
import tech.jcjc.crashcollection.collector.StackInfoCollector;
import tech.jcjc.crashcollection.crashupload.CrashCheckUpload;
import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.FileUtils;
import tech.jcjc.crashcollection.utils.ProcessLock;

public class CrashReportImpl implements UncaughtExceptionHandler {
    private static final String TAG = "CrashReportImpl";

    private final Context mContext;

    private static CrashReportImpl sInstance;

    private UncaughtExceptionHandler mDefaultHandler;

    private static AtomicBoolean bIsRunning = new AtomicBoolean(false);

    private CrashCheckUpload crashUpload;

    private ICrashInterface crashInterface;

    private final List<ICrashCollector> crashCollector = new ArrayList<ICrashCollector>();

    public static final int JAVA_CRASH_TYPE = 0;

    public static final int NATIVE_CRASH_TYPE = 1;

    public static final int BREAKPAD_CAUGHT_CRASH_TYPE = 2;

    private static final String CRASH_SHARE_PREF_NAME = "crash_config";

    private static final String TIME_OUT_EXCEPTION_RECORD = "time_out_ex_time_stamp";

    private CrashReportImpl(Context context) {
        mContext = context;
    }

    public static CrashReportImpl getInstance(Context context) {
        synchronized (CrashReportImpl.class) {
            if (sInstance == null) {
                sInstance = new CrashReportImpl(context);
            }
            return sInstance;
        }
    }

    public void Init(ICrashInterface c) {
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        try {
            crashInterface = c;
            crashUpload = new CrashCheckUpload(mContext, crashInterface);

            crashCollector.add(new BaseInfoCollector());
            crashCollector.add(new MemoryInfoCollector());
            crashCollector.add(new StackInfoCollector());
            // crashCollector.add(new ObjectInfoCollector());
            crashCollector.add(new CustomInfoCollector());
            crashCollector.add(new ContextInfoCollector());
            for (ICrashCollector collector : crashCollector) {
                collector.Init(mContext, c);
            }
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        }
    }

    public boolean isRunning() {
        return bIsRunning.get();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // 立即输出错误
        Log.e("CrashHandler", ex != null ? ex.getMessage() : "null", ex);

        // TODO yjl
//        if (IPC.isPersistentProcess() && ex instanceof TimeoutException) {
//            if (ex.getMessage().contains("os.BinderProxy.finalize()")) {
//                SharedPreferences preferences = Pref.getSharedPreferences(CRASH_SHARE_PREF_NAME);
//                preferences.edit().putString(TIME_OUT_EXCEPTION_RECORD, String.valueOf(System.currentTimeMillis())).commit();
//                System.gc();
//
//                return;
//            }
//        }

        CollectorThread collectThread = handleException(JAVA_CRASH_TYPE, thread, ex);

        try {
            if (collectThread != null) {
                collectThread.join(10000);
            } else {
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        crashInterface.uncaughtExceptionResultHandler(JAVA_CRASH_TYPE, collectThread.getCollectorRootDir(), collectThread.getCollectorType(), thread, ex);

        switch (collectThread.getCollectorType()) {
            case emDefault: {
                if (mDefaultHandler != null) {
                    mDefaultHandler.uncaughtException(thread, ex);
                    bIsRunning.set(false);
                    return;
                }
            }
            break;

            case emSkip: {
                Log.e(TAG, "uncaughtException is Skip");
            }
            break;
        }
        if (AppEnv.ISAPPDEBUG) {
            Log.d(TAG, "Crash Handler Complete!!!!. exit it");
        }
        bIsRunning.set(false);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public int nativeUncaughtException(String arg0, String arg1) {
        CollectorThread collectThread = handleException(NATIVE_CRASH_TYPE, Thread.currentThread(), arg0);
        try {
            if (collectThread != null) {
                collectThread.join(10000);
                bIsRunning.set(false);
                crashInterface.uncaughtExceptionResultHandler(NATIVE_CRASH_TYPE, collectThread.getCollectorRootDir(), collectThread.getCollectorType(), null, null);
                return collectThread.getCollectorType().ordinal();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public int checkUploadCrashFile(Map<String, String> exts, boolean uploadOnNewThread) {
        //检查Breakpad捕获的log日志里有没有crashreport，如果没有则创建一份
        CrashReportImpl.getInstance(mContext).checkBreakpadBaseInfoFile();
        return crashUpload.checkUploadCrashFile(exts, uploadOnNewThread);
    }

    public int checkUploadCrashFile(File rootDir, Map<String, String> exts, int maxNum) {
        return crashUpload.checkUploadCrashFile(rootDir, exts, maxNum);
    }

    public String getDefaultNativeCrashFolder() {
        return crashUpload.getDefaultNativeCrashFolder();
    }

    public String getNativeCrashFolder() {
        return crashUpload.getNativeCrashFolder();
    }

    public String getJavaCrashFolder() {
        return crashUpload.getJavaCrashFolder();
    }

    class CollectorThread extends Thread {
        private final Thread thread;

        private final Object ex;

        private final int type;

        private String rootDir;

        private ICrashInterface.ExceptionAction action;

        public CollectorThread(final int arg0, final Thread arg1, final Object arg2) {
            type = arg0;
            thread = arg1;
            ex = arg2;
        }

        @Override
        public void run() {
            ICrashCollector.FilePrintWriter fileWriter = new ICrashCollector.FilePrintWriter(rootDir);
            // TODO yjl
//            try {
//                if (IPC.isPersistentProcess() && ex instanceof OutOfMemoryError) {
//                    long now = System.currentTimeMillis();
//                    SharedPreferences preferences = Pref.getSharedPreferences(CRASH_SHARE_PREF_NAME);
//                    String timeStampStr = preferences.getString(TIME_OUT_EXCEPTION_RECORD, "0");
//                    long lastTimeoutExceptionStamp = Long.parseLong(timeStampStr);
//                    long duration = now - lastTimeoutExceptionStamp;
//                    if (duration > 0 && duration < 2000) {
//                        fileWriter.setFile(ICrashCollector.SUMMARY_FILE);
//                        fileWriter.println("A TimeoutException is thrown at " + duration / 1000 + " seconds before");
//                    }
//                }
//            } catch (Exception e) {
//                //do nothing
//            }

            // 为了避免logcat丢失的现象，提取收取logcat
            for (ICrashCollector collector :
                    crashCollector) {
                try {
                    collector.preCollect(type, thread, ex, fileWriter);
                } catch (Throwable e) {
                    e.printStackTrace();
                } finally {
                    fileWriter.flush();
                }
            }
            for (ICrashCollector collector : crashCollector) {
                try {
                    collector.runCollector(type, thread, ex, fileWriter);
                } catch (Throwable e) {
                    e.printStackTrace();
                } finally {
                    fileWriter.flush();
                }
            }
            fileWriter.close();
            
            for (ICrashCollector collector :
                    crashCollector) {
                try {
                    collector.postCollect(type, thread, ex);
                } catch (Throwable e) {
                    e.printStackTrace();
                } finally {
                    fileWriter.flush();
                }
            }
        }

        public String getCollectorRootDir() {
            return rootDir;
        }

        public ICrashInterface.ExceptionAction getCollectorType() {
            return action;
        }

        public void setType(ICrashInterface.ExceptionAction arg0) {
            action = arg0;
        }

        public void setRootDir(String arg0) {
            rootDir = arg0;
        }
    }

    private CollectorThread handleException(int type, final Thread thread, final Object ex) {
        CollectorThread collectThread = null;
        try {
            collectThread = new CollectorThread(type, thread, ex);
            collectThread.setType(ICrashInterface.ExceptionAction.emSkip);
            if (thread == null || ex == null) {
                collectThread.setType(ICrashInterface.ExceptionAction.emDefault);
                return collectThread;
            }

            if (!bIsRunning.compareAndSet(false, true)) {
                collectThread.setType(ICrashInterface.ExceptionAction.emSkip);
                return collectThread;
            }

            crashInterface.uncaughtExceptionPreHandler(type, thread, ex);

            //先决定是否运行Handler收集日志
            if (!crashInterface.shouldRunHandler(type, thread, ex)) {
                collectThread.setType(ICrashInterface.ExceptionAction.emRestart);
                return collectThread;
            }

            //再决定是否弹窗
            collectThread.setType(crashInterface.getCrashCollectorType(type, thread, ex));

            String crashFolder = crashInterface.getCrashRootFolder(type, thread, ex);

            collectThread.setRootDir(crashFolder);
            File cFile = new File(collectThread.getCollectorRootDir());
            try {
                if (cFile.isDirectory()) {
                    FileUtils.deleteDir(cFile.getAbsolutePath());
                } else {
                    cFile.delete();
                }
                if (!cFile.mkdirs()) {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            collectThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return collectThread;
    }


    public boolean checkBreakpadBaseInfoFile() {
        final String breakpadCrashRootPath = CrashReportImpl.getInstance(mContext).getDefaultNativeCrashFolder();
        File breakpadRootFile = new File(breakpadCrashRootPath);
        if (!breakpadRootFile.exists() || !breakpadRootFile.isDirectory()) {
            return false;
        } else if (breakpadRootFile.isDirectory()) {
            File[] files = breakpadRootFile.listFiles();
            if (files == null || files.length == 0) {
                if (AppEnv.ISAPPDEBUG) {
                    Log.d(TAG, "There is nothing under " + breakpadCrashRootPath);
                }
                return false;
            }
        }

        File crashreport = new File(breakpadCrashRootPath, ICrashCollector.SUMMARY_FILE);
        if (crashreport.exists() && crashreport.isFile()) {
            if (AppEnv.ISAPPDEBUG) {
                Log.d(TAG, "the file has exist, return");
            }
            return true;
        } else {
            if (AppEnv.ISAPPDEBUG) {
                Log.d(TAG, "try to create new BaseFile");
            }
        }
        ProcessLock processLock = new ProcessLock(mContext, "breakpad_lock", true);
        try {
            processLock.tryLock(1, 0, false);
            if (AppEnv.ISAPPDEBUG) {
                Log.d(TAG, "begin collect log for breakpad");
            }
            ICrashCollector.FilePrintWriter filePrintWriter = new ICrashCollector.FilePrintWriter(breakpadCrashRootPath);
            filePrintWriter.println("-------------Native crash caught by Breakpad------------");
            BaseInfoCollector baseInfoCollector = new BaseInfoCollector();
            baseInfoCollector.Init(mContext, sInstance.crashInterface);
            baseInfoCollector.runCollector(BREAKPAD_CAUGHT_CRASH_TYPE, Thread.currentThread(), null, filePrintWriter);   //只收集基本信息

            filePrintWriter.flush();
            filePrintWriter.close();

            if (AppEnv.ISAPPDEBUG) {
                Log.d(TAG, "end to collect base info");
            }
            return true;
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
            return false;
        } finally {
            processLock.freeLock();
        }
    }
}
