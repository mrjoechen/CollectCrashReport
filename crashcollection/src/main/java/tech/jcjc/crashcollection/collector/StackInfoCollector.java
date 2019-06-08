package tech.jcjc.crashcollection.collector;

import android.content.Context;
import android.text.TextUtils;


import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import tech.jcjc.crashcollection.AppEnv;
import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.ByteConvertor;
import tech.jcjc.crashcollection.utils.FileUtils;
import tech.jcjc.crashcollection.utils.SecurityUtil;

public class StackInfoCollector extends ICrashCollector {
    private Context mContext;

    private static final String TAG = "[STACK_TRACE]";

    public static final String PKG_IGNORE_LIST[] = {
            "android.", "com.android.", "java.", "com.java.", "dalvik.", "libcore.", "de.robv.", "org.apache", "com.lbe", "com.qihoo360.mobilesafe.mt.SafeAsyncTask"
    };

    private static final int MAX_STACK_TRACE_LIMIT = 50;

    private final String mBuildPropFilePath = "/system/build.prop";

    private final int mBuildPropToFileFinish = 0;

    private final int mBuildPropNotExist = 1;

    private final int mBuildPropCannotRead = 2;

    private final int mBuildPropToFileException = 3;

    private final String mSimpleDateFormat = "yyyy-MM-dd hh:mm:ss";

    @Override
    public void Init(Context context, ICrashInterface c) {
        mContext = context;
    }

    @Override
    public void runCollector(int type, Thread thread, Object ex, FilePrintWriter printWriter) {
        printWriter.setFile(SUMMARY_FILE);
        printWriter.println(TAG);
        try {
            printWriter.println(makeupKeyValue("THREAD_INFO", getCrashThreadInfo(thread)));
            printWriter.println(makeupKeyValue("LAST_CALL", getCrashLastCallStackTrace(ex)));
            String stackTrace = getCauseStackTrace(ex);
            printWriter.println(makeupKeyValue("STACK_TRACE", stackTrace));

            //由于CrashHandler经常没有执行CustomInfoCollector就结束了，故将这些信息提前收集
//            getBuildPropMsg(printWriter);
//            getFileHierarchy(mContext.getFilesDir().getParent(), printWriter.getRootDirectory(), "fileHierarchy.log");
            getProcessToFile(printWriter.getRootDirectory(), "ps.log");
        } catch (Exception e) {
            e.printStackTrace();
            printWriter.println(makeupKeyValue("STACK_TRACE", getCauseStackTrace(e)));
        }
        printWriter.println();
    }

    public static String getCrashLastCallStackTrace(Object ex) {
        try {
            if (ex != null && ex instanceof Throwable) {
                try {
                    String lastCall = null;
                    Throwable cause = (Throwable) ex;
                    do {
                        if (cause.getStackTrace() != null) {
                            StackTraceElement[] stacks = cause.getStackTrace();
                            if (stacks.length > 0) {
                                for (StackTraceElement stack : stacks) {
                                    if (!isIgnoreList(stack.getClassName())) {
                                        lastCall = stack.getClassName() + "." + stack.getMethodName() +
                                                "(" + stack.getFileName() + ":" + stack.getLineNumber() + ")";
                                        break;
                                    }
                                }
                            }
                        }

                        if (!TextUtils.isEmpty(lastCall)) {
                            break;
                        }
                        cause = cause.getCause();
                    } while (cause != null && cause != ex);

                    return TextUtils.isEmpty(lastCall) ? ((Throwable) ex).getStackTrace()[0].getClassName() : lastCall;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (ex != null && ex instanceof String && !TextUtils.isEmpty((String) ex)) {
                return "native:" + ((String) ex).split("\n")[0];
            }
        } catch (Exception e) {
            // ignore
        }

        return "UNKNOW_LAST_CALL";
    }

    private String getCrashThreadInfo(Thread thread) {
        try {
            return "id:" + thread.getId() + " name:" + thread.getName() + " priority:" + thread.getPriority() + " state:" + thread.getState().toString();
        } catch (Exception e) {
            // ignore
        }
        return "unknow";
    }

    private String getCauseStackTrace(Object ex) {
        try {
            if (ex != null && ex instanceof Throwable) {
                Throwable cause = (Throwable) ex;
                if (cause.getStackTrace() != null) {
                    Writer info = null;
                    try {
                        info = new StringWriter();
                        PrintWriter printWriter = new PrintWriter(info);
                        try {
                            do {
                                cause.printStackTrace(printWriter);
                                if (printWriter.checkError()) {
                                    break;
                                }
                                cause = cause.getCause();
                            } while (cause != null && cause != ex);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return throwableFormat(e);
                        }
                        String stack = info.toString();
                        return TextUtils.isEmpty(stack) ? "unknow" : stack;
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            if (info != null) {
                                info.close();
                            }
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }

                }
            } else if (ex != null && ex instanceof String && !TextUtils.isEmpty((String) ex)) {
                return "native:" + (String) ex;
            }
        } catch (Exception e) {
            // ignore
        }

        return "unknow:exception";
    }

    public static String getThrowableHashCode(Object ex) {
        try {
            if (ex != null && ex instanceof Throwable) {
                Throwable cause = (Throwable) ex;
                if (cause != null && cause.getStackTrace() != null) {
                    try {
                        int count = 0;
                        MessageDigest mdAll = MessageDigest.getInstance("MD5");
                        MessageDigest mdSafe = MessageDigest.getInstance("MD5");
                        boolean bHasMobilesafe = false;

                        do {
                            StackTraceElement[] stacks = cause.getStackTrace();
                            if (stacks.length > 0) {
                                for (StackTraceElement stack : stacks) {
                                    // 由于Android底层函数栈的不确定性， 这里我们只计算本地包名下的栈HASH
                                    String hashString = stack.getClassName() + stack.getMethodName();
                                    if (!isIgnoreList(stack.getClassName())) {
                                        bHasMobilesafe = true;
                                        // 只有本地代码才保留行号
                                        hashString += stack.getLineNumber();
                                        mdSafe.update(hashString.getBytes());
                                    }
                                    count++;
                                    mdAll.update(hashString.getBytes());
                                }
                            }

                            if (count >= MAX_STACK_TRACE_LIMIT) {
                                break;
                            }
                            cause = cause.getCause();
                        } while (cause != null && cause != ex);

                        MessageDigest mdDigest = bHasMobilesafe ? mdSafe : mdAll;
                        return ByteConvertor.bytesToHexString(mdDigest.digest()).toUpperCase();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (ex != null && ex instanceof String && TextUtils.isEmpty((String) ex)) {
                return SecurityUtil.getMD5((String) ex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "00000000000000000000000000000000";
    }

    private static boolean isIgnoreList(String className) {
        for (String ignore : PKG_IGNORE_LIST) {
            if (className.startsWith(ignore)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 打印获取uild.Prop信息的结果
     *
     * @param printWriter printWriter
     */
    private void getBuildPropMsg(FilePrintWriter printWriter) {
        int buildPropToFileResult = getBuildPropToFile(new File(printWriter.getRootDirectory(), "build.prop"));
        switch (buildPropToFileResult) {
            case mBuildPropNotExist:
                printWriter.println("==The build.prop file of this device is not exist !==");
                break;
            case mBuildPropCannotRead:
                printWriter.println("==The build.prop file of this device cannot be read !==");
                break;
            case mBuildPropToFileException:
                printWriter.println("==Got exception while getting build.prop to File !==");
                break;
            default:
                break;
        }
    }

    private int getBuildPropToFile(File logFile) {
        try {
            File proFile = new File(mBuildPropFilePath);
            if (proFile.exists() && proFile.canRead()) {
                FileUtils.copyFile(proFile, logFile);
                return mBuildPropToFileFinish;
            } else if (!proFile.exists()) {
                return mBuildPropNotExist;
            } else {
                return mBuildPropCannotRead;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return mBuildPropToFileException;
        }
    }

    private void getProcessToFile(String rootDirectory, final String fileName) {
        FilePrintWriter printWriter = null;
        try {
            printWriter = new FilePrintWriter(rootDirectory);
            printWriter.setFile(fileName);
            ArrayList<String> args = new ArrayList<String>();
            args.add("ps");
            Process process = Runtime.getRuntime().exec(args.toArray(new String[args.size()]));
            InputStreamReader streamReader = new InputStreamReader(process.getInputStream());
            try {
                BufferedReader bufferedReader = new BufferedReader(streamReader, 1024);
                String line = bufferedReader.readLine();
                while (line != null) {
                    printWriter.println(line);
                    line = bufferedReader.readLine();
                }
            } catch (Exception e) {
                if (AppEnv.ISAPPDEBUG) {
                    e.printStackTrace();
                }
            } finally {
                try {
                    streamReader.close();
                    process.destroy();
                } catch (Exception e) {
                    if (AppEnv.ISAPPDEBUG) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        } finally {
            if (printWriter != null) {
                try {
                    printWriter.flush();
                    printWriter.close();
                } catch (Exception e) {
                    if (AppEnv.ISAPPDEBUG) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    private void getFileHierarchy(String recordDir, String root, String logFile) {
        FilePrintWriter filePrint = null;
        try {
            filePrint = new FilePrintWriter(root);
            filePrint.setFile(logFile);
            filePrint.println(recordDir);
            StringBuffer sb = new StringBuffer();
            getFileHierarchy(new File(recordDir), sb, new StringBuffer());
            filePrint.println(sb.toString());
            filePrint.println();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (filePrint != null) {
                    filePrint.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    private void getFileHierarchy(File file, StringBuffer sb, StringBuffer space) {
        if (file.isDirectory()) {
            sb.append(space).append("+").append(file.getName());
            StringBuffer sbDir = new StringBuffer();
            StringBuffer spaceDir = new StringBuffer(space + " ");
            File[] files = file.listFiles();
            List<File> list = sort(files);
            for (File f : list) {
                getFileHierarchy(f, sbDir, spaceDir);
            }

            sb.append(" ").append(getDateStr(file.lastModified())).append("\n").append(sbDir);
        } else {
            long fSize = file.length();
            sb.append(space).append(file.getName());
            sb.append(" ").append(fSize).append(" ").append(getDateStr(file.lastModified())).append("\n");
        }
    }

    private String getDateStr(long time) {
        SimpleDateFormat sdf = new SimpleDateFormat(mSimpleDateFormat);
        return sdf.format(new Date(time));
    }

    private List<File> sort(File[] list) {
        List<File> files = Arrays.asList(list);
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile()) {
                    return -1;
                }
                if (o1.isFile() && o2.isDirectory()) {
                    return 1;
                }
                return o1.getName().compareTo(o2.getName());
            }
        });
        return files;
    }
}
