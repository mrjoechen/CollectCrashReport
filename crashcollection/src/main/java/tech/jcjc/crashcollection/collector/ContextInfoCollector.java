package tech.jcjc.crashcollection.collector;

import android.content.Context;


import java.io.File;
import java.util.ArrayList;

import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.FileUtils;


public class ContextInfoCollector extends ICrashCollector {
    
    private static final long MAX_LOGCAT_LIMIT = 2000;
    private final String LOG_FILE_NAME = "logcat.log";
    private static final long HANDLER_TIME = 6;
    
    private static final String TAG = "[CONTEXT]";

    private String mLogFilePath;
    private Process logcatProc;
    
    private Context mContext;
    private ICrashInterface mCrashInterface;
    @Override
    public void Init(Context context, ICrashInterface c) {
        mContext = context;
        mCrashInterface = c;
    }

    @Override
    public void preCollect(int type, Thread thread, Object ex, FilePrintWriter printWriter) {
        mLogFilePath = printWriter.getRootDirectory() + LOG_FILE_NAME;
        logcatProc = getLogcatToFile(mLogFilePath, MAX_LOGCAT_LIMIT);
    }

    @Override
    public void runCollector(int type, Thread thread, Object ex, FilePrintWriter printWriter) {
        killIndividualProc(logcatProc);
        printWriter.setFile(SUMMARY_FILE);
        printWriter.println(TAG);
        
        File logcatFile = new File(mLogFilePath);
        if (logcatFile.exists()) {
            byte[] logcatBytes = FileUtils.readFileByte(logcatFile);
            if (logcatBytes != null) {
                String logcatStr = new String(logcatBytes);
                if (!"".equals(logcatStr)) {
                    printWriter.println(makeupKeyValue("LOGCAT", logcatStr));
                }
            }
            
        }
        printWriter.println();
    }

    @Override
    public void postCollect(int type, Thread thread, Object ex) {
        if (mLogFilePath != null) {
            File file = new File(mLogFilePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    /* 单独起一个进程收集logcat */
    private Process getLogcatToFile(String filePath, long limit) {
        ArrayList<String> cmdLine = new ArrayList<String>();
        cmdLine.add("logcat");
        cmdLine.add("-d");
        cmdLine.add("-v");
        cmdLine.add("time");
        cmdLine.add("-t");
        cmdLine.add(String.valueOf(limit));
        cmdLine.add("-f");
        cmdLine.add(filePath);

        try {
            return Runtime.getRuntime().exec(cmdLine.toArray(new String[cmdLine.size()]));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /* 在某些三星机型上，logcat输出进程不能自动退出，故每隔1秒钟查看一次，最后仍未退出则杀死 */
    private void killIndividualProc(Process vLogProc) {
        if (null == vLogProc) {
            return;
        }
        long count = HANDLER_TIME;
        try {
            do {
                try {
                    vLogProc.exitValue();
                    break;
                } catch (IllegalThreadStateException illegThreadStateEx) {
                    Thread.sleep(1000);
                    count--;
                    continue;
                } catch (Exception e) {
                    if (count == HANDLER_TIME) {
                        try {
                            Thread.sleep(2000);
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                    break;
                }
            } while (count > 0);

            vLogProc.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return;
    }
}
