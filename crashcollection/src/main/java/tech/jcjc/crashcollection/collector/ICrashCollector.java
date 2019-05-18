package tech.jcjc.crashcollection.collector;

import android.app.ActivityManager;
import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;

import tech.jcjc.crashcollection.interfaces.ICrashInterface;

public abstract class ICrashCollector {
    public abstract void Init(Context context, ICrashInterface c);

    /**
     * 收集前的准备工作
     * @param type
     * @param thread
     * @param ex
     * @param printWriter
     */
    public void preCollect(int type, Thread thread, Object ex, FilePrintWriter printWriter){}

    /**
     * 收集信息
     * @param type
     * @param thread
     * @param ex
     * @param printWriter
     */
    public abstract void runCollector(int type, Thread thread, Object ex, FilePrintWriter printWriter);

    /**
     * 收集完成后的清理工作
     * @param type
     * @param thread
     * @param ex
     */
    public void postCollect(int type, Thread thread, Object ex){};

    public static final String SUMMARY_FILE = "crash_report";

    private static String mProcessName;

    public static class FilePrintWriter {
        private String fileName;

        private final String rootDir;

        private FileWriter fileWriter;

        public FilePrintWriter(String arg0) {
            rootDir = arg0;
        }

        public void setFile(String arg0) {
            if (!arg0.equals(fileName)) {
                fileName = arg0;
                if (fileWriter != null) {
                    close();
                }

                try {
                    fileWriter = new FileWriter(getFilePath(), true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public String getFilePath() {
            return rootDir + File.separator + fileName;
        }

        public String getRootDirectory() {
            return rootDir + File.separator;
        }

        public void println(String data) {
            if (fileWriter != null) {
                try {
                    fileWriter.append(data + "\r\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void println() {
            println("");
        }

        public void flush() {
            if (fileWriter != null) {
                try {
                    fileWriter.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void close() {
            if (fileWriter != null) {
                try {
                    fileWriter.flush();
                    fileWriter.close();
                    fileWriter = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }

    protected String throwableFormat(Throwable ex) {
        String stacks = "";
        if (ex != null && ex.getStackTrace().length > 0) {
            if (ex.getLocalizedMessage() != null) {
                return ex.getLocalizedMessage();
            } else if (ex.getMessage() != null) {
                return ex.getMessage();
            } else {
                StackTraceElement[] stackTraceElements = ex.getStackTrace();
                for (StackTraceElement stack : stackTraceElements) {
                    stacks += "[" + stack.getLineNumber() + "]" + stack.getClassName() + ":" + stack.getMethodName();
                }
            }
        }
        return stacks;
    }

    protected String getProcessName(Context context) {
        if (TextUtils.isEmpty(mProcessName)) {
            int pid = android.os.Process.myPid();
            try {
                mProcessName = "unknow";
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                for (ActivityManager.RunningAppProcessInfo processInfo : am.getRunningAppProcesses()) {
                    if (processInfo.pid == pid) {
                        mProcessName = processInfo.processName;
                    }
                }
                return mProcessName + ":" + String.valueOf(pid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return mProcessName;
    }

    protected String makeupKeyValue(String key, String value) {
        if (value.contains("\n")) {
            // python解析*.ini文件时，value如果跨行，要求除第一行以外都要有缩进，才能正确解析
            value = value.replaceAll("\n", "\n\t");
            return key + "=\"\r\n\t" + value + "\t\"";
        } else if (value.contains(" ")) {
            return key + "=\"" + value + "\"";
        }
        return key + "=" + value;
    }

    protected String getFileProperties(String file, String key) {
        FileInputStream fileStream = null;
        try {
            String value = null;
            Properties prop = new Properties();
            fileStream = new FileInputStream(new File(file));
            if (fileStream != null) {
                prop.load(fileStream);
                value = prop.getProperty(key);
            }
            return TextUtils.isEmpty(value) ? "UNKNOW" : value;
        } catch (Exception e) {
            return throwableFormat(e);
        } finally {
            try {
                fileStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

//    protected String getSharePref(String key, String prefName) {
//        try {
//            return IpcPrefHelper.getString(key, "unknow", prefName);
//        } catch (Exception e) {
//            return "unknow";
//        }
//    }
}
