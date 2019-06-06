package tech.jcjc.crashcollection.collector;


import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.text.format.Formatter;


import java.io.File;

import tech.jcjc.crashcollection.AppEnv;
import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.FileUtils;

public class MemoryInfoCollector extends ICrashCollector {
    private Context mContext;

    private static final String TAG = "[MEMORY]";

    private static final String PROCESS_STATUS_LOG = "process_status.txt";

    private static final String PROCESS_MAPS_FILE = "process_maps.txt";

    private static final String XBIN_DIR_FILES_NAME = "system_xbin.txt";

    private static final String BIN_DIR_FILES_NAME = "system_bin.txt";

    private static final String SYSTEM_MEMORY = "SYS_MEMORY";

    private static final String DALVIK_MEMORY = "DALVIK_MEMORY";

    private static final String NATIVE_MEMORY = "NATIVE_MEMORY";

    private static final String OTHER_MEMORY = "OTHER_MEMORY";

    private ActivityManager activityManager;

    @Override
    public void Init(Context context, ICrashInterface c) {
        mContext = context;
        activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Override
    public void runCollector(int type, Thread thread, Object ex, FilePrintWriter printWriter) {
        printWriter.setFile(SUMMARY_FILE);
        printWriter.println(TAG);
        try {
            printWriter.println(makeupKeyValue(SYSTEM_MEMORY, getMemoryUsage()));
            printWriter.println(makeupKeyValue(DALVIK_MEMORY, getDalvikMemoryUsage()));
            printWriter.println(makeupKeyValue(NATIVE_MEMORY, getNativeMemoryUsage()));
            printWriter.println(makeupKeyValue(OTHER_MEMORY, getOtherMemoryUsage()));

//            listBinDirectory(printWriter.getRootDirectory());
//            listXbinDirectory(printWriter.getRootDirectory());

            int pid = android.os.Process.myPid();
            getProcessStatusMsg(pid, printWriter);
//            getProcessMapMsg(pid, printWriter);
        } catch (Exception e) {
            // ignore
        }
        printWriter.println();
    }

    private String getMemoryUsage() {
        try {
            long totalMemory = Runtime.getRuntime().totalMemory();
            long freeMemory = Runtime.getRuntime().freeMemory();
            return Formatter.formatFileSize(mContext, totalMemory - freeMemory) + "/" + Formatter.formatFileSize(mContext, totalMemory);
        } catch (Exception e) {
            return throwableFormat(e);
        }
    }

    private String getDalvikMemoryUsage() {
        try {
            int pids[] = {
                    android.os.Process.myPid()
            };
            Debug.MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(pids);
            if (memoryInfoArray != null && memoryInfoArray.length > 0) {
                Debug.MemoryInfo memoryInfo = memoryInfoArray[0];
                return "pss:" + Formatter.formatFileSize(mContext, memoryInfo.dalvikPss * 1024) + "/" +
                        "share:" + Formatter.formatFileSize(mContext, memoryInfo.dalvikSharedDirty * 1024) + "/" +
                        "private" + Formatter.formatFileSize(mContext, memoryInfo.dalvikPrivateDirty * 1024);
            }
        } catch (Exception e) {
            return throwableFormat(e);
        }
        return "unknown";
    }

    private String getNativeMemoryUsage() {
        try {
            int pids[] = {
                    android.os.Process.myPid()
            };
            Debug.MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(pids);
            if (memoryInfoArray != null && memoryInfoArray.length > 0) {
                Debug.MemoryInfo memoryInfo = memoryInfoArray[0];
                return "pss:" + Formatter.formatFileSize(mContext, memoryInfo.nativePss * 1024) + "/" +
                        "share:" + Formatter.formatFileSize(mContext, memoryInfo.nativeSharedDirty * 1024) + "/" +
                        "private" + Formatter.formatFileSize(mContext, memoryInfo.nativePrivateDirty * 1024);
            }
        } catch (Exception e) {
            return throwableFormat(e);
        }
        return "unknow";
    }

    private String getOtherMemoryUsage() {
        try {
            int pids[] = {
                    android.os.Process.myPid()
            };
            Debug.MemoryInfo[] memoryInfoArray = activityManager.getProcessMemoryInfo(pids);
            if (memoryInfoArray != null && memoryInfoArray.length > 0) {
                Debug.MemoryInfo memoryInfo = memoryInfoArray[0];
                return "pss:" + Formatter.formatFileSize(mContext, memoryInfo.otherPss * 1024) + "/" +
                        "share:" + Formatter.formatFileSize(mContext, memoryInfo.otherSharedDirty * 1024) + "/" +
                        "private" + Formatter.formatFileSize(mContext, memoryInfo.otherPrivateDirty * 1024);
            }
        } catch (Exception e) {
            return throwableFormat(e);
        }
        return "unknow";
    }

    /**
     * 获取/proc/pid/status文件
     *
     * @param pid         进程pid
     * @param printWriter printWriter
     */
    private void getProcessStatusMsg(int pid, FilePrintWriter printWriter) {
        try {
            File processStatusFile = new File("/proc/" + pid + "/status");
            if (processStatusFile.exists() && processStatusFile.canRead()) {
                File logFile = new File(printWriter.getRootDirectory(), PROCESS_STATUS_LOG);
                FileUtils.copyFile(processStatusFile, logFile);
            }
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 拷贝/proc/pid/maps文件
     *
     * @param pid         进程pid
     * @param printWriter printWriter
     */
    private void getProcessMapMsg(int pid, FilePrintWriter printWriter) {
        try {
            File processMapFile = new File("/proc/" + pid + "/maps");
            if (processMapFile.exists() && processMapFile.canRead()) {
                File logFile = new File(printWriter.getRootDirectory(), PROCESS_MAPS_FILE);
                FileUtils.copyFile(processMapFile, logFile);
            }
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        }
    }

    private void listXbinDirectory(String rootDirectory) {
        FilePrintWriter printWriter = null;
        try {
            printWriter = new FilePrintWriter(rootDirectory);
            printWriter.setFile(XBIN_DIR_FILES_NAME);
            File xbinDirectory = new File("/system/xbin/");
            if (xbinDirectory.exists()) {
                listFilesForFolder(xbinDirectory, printWriter);
            }
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        } finally {
            if (printWriter != null) {
                printWriter.flush();
                printWriter.close();
            }
        }
    }

    private void listBinDirectory(String rootDirectory) {
        FilePrintWriter printWriter = null;
        try {
            printWriter = new FilePrintWriter(rootDirectory);
            printWriter.setFile(BIN_DIR_FILES_NAME);
            File binDirectory = new File("/system/bin/");
            if (binDirectory.exists()) {
                listFilesForFolder(binDirectory, printWriter);
            }
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        } finally {
            if (printWriter != null) {
                printWriter.flush();
                printWriter.close();
            }
        }
    }

    private void listFilesForFolder(final File folder, FilePrintWriter printWriter) {
        for (final File fileEntry : folder.listFiles()) {
            printWriter.println(fileEntry.getAbsolutePath());
        }
    }
}
