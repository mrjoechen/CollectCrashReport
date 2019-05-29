package tech.jcjc.crashcollection.crashupload;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tech.jcjc.crashcollection.AppEnv;
import tech.jcjc.crashcollection.collector.ICrashCollector;
import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.FileUtils;
import tech.jcjc.crashcollection.utils.ProcessLock;

public class CrashCheckUpload {
    private static final String TAG = "CrashCheckUpload";

    private final Context mContext;

    private static final String CRASH_FOLDER = "crash";

    private static final String NATIVE_CRASH_FOLDER = "native_crash";

    private static final String DEFAULT_NATIVE_CRASH_FOLDER = "default_native_crash";

    private static String mCrashRootPath = "";

    private static final String BREAKPAD_DUMP_SUFFIX = ".dmp";

    private static final String BREAKPAD_DUMP_INFO_SUFFIX = ".dmp.info";

    private static final String BREAKPAD_DUMP_LOG_SUFFIX = ".dmp.log";

    private static final int BREAKPAD_MAX_UPLOAD_FILES = 5;

    private static final long MAX_CRASH_DIR_EXIST_TIME = 30 * 24 * 3600 * 1000L;

    private final ICrashInterface mCrashInterface;

    public CrashCheckUpload(Context context, ICrashInterface c) {
        mContext = context;
        mCrashInterface = c;
    }

    private class UploadThread extends Thread {
        private int mUploadResult;

        private final Map<String, String> mParams;

        public UploadThread(Map<String, String> ex, String threadName) {
            super(threadName);
            mParams = ex;
        }

        public int getUploadResult() {
            return mUploadResult;
        }

        @Override
        public void run() {
            int[] javaCount = postJavaCrashFile(mParams);
            int[] nativeCount = postNativeCrashFile(mParams);
            //增加对Breakpad的进行处理
            int[] breakpadCount = postBreakpadCaughtCrashFile(mParams);
            mUploadResult = javaCount[0] + nativeCount[0] + breakpadCount[0];

            if (mUploadResult == 0 && javaCount[1] == 0 && nativeCount[1] == 0 && breakpadCount[1] == 0) {
                mUploadResult = -1; // 没有失败上传,我们也认为是成功
            }
            mCrashInterface.crashUploadResultHandler(javaCount, nativeCount, breakpadCount);
        }
    }


    /**
     * 传递一个文件列表，来进行压缩上传
     *
     * @param crashFileList 文件列表
     * @param exts          附加参数
     * @return 上传结果
     */
    private int reportCrashDataForm(final ArrayList<File> crashFileList, Map<String, String> exts) {
        UploadByFileListTask upload = new UploadByFileListTask(mContext, mCrashInterface, crashFileList, exts);
        try {
            return upload.doUpload();
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    /**
     * 向服务器发送一个目录下的所有数据
     *
     * @param crashRootDir 根目录
     * @param exts         参数
     * @return 发送结果
     */
    private int reportCrashDataForm(final File crashRootDir, Map<String, String> exts) {
        UploadByDirTask upload = new UploadByDirTask(mContext, mCrashInterface, crashRootDir, exts);
        try {
            return upload.doUpload();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public String getJavaCrashFolder() {
        return getMobilesafeCrashFolder(CRASH_FOLDER);
    }

    public String getNativeCrashFolder() {
        return getMobilesafeCrashFolder(NATIVE_CRASH_FOLDER);
    }

    public String getDefaultNativeCrashFolder() {
        return getMobilesafeCrashFolder(DEFAULT_NATIVE_CRASH_FOLDER);
    }

    private synchronized String getMobilesafeCrashFolder(String crashName) {
        if (TextUtils.isEmpty(mCrashRootPath)) {
            if (getSDCardFreeStorage() > 10 * 1024L && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                mCrashRootPath += (Environment.getExternalStorageDirectory().getPath() + "/360/" + mCrashInterface.getProduct() + File.separator);
            }

            // 测试下防止出现没权限的情况
            if (!TextUtils.isEmpty(mCrashRootPath)) {
                try {
                    //提前创建目录，避免因为该目录不存在，导致创建文件失败，从而在/data/data放置崩溃日志
                    File crashRootPathFile = new File(mCrashRootPath);
                    if (!crashRootPathFile.exists() || !crashRootPathFile.isDirectory()) {
                        crashRootPathFile.mkdirs();
                    }

                    File testFile = new File(mCrashRootPath, "test");
                    testFile.createNewFile();
                    testFile.delete();
                } catch (Exception e) {
                    mCrashRootPath = "";
                    if (AppEnv.ISAPPDEBUG) {
                        e.printStackTrace();
                    }
                }
            }

            if (TextUtils.isEmpty(mCrashRootPath)) {
                mCrashRootPath += (mContext.getFilesDir() + File.separator);
            }
        }

        return mCrashRootPath + crashName + File.separator;
    }

    private static List<File> sort(File[] list) {
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
                return o2.getName().compareTo(o1.getName());
            }
        });
        return files;
    }

    private boolean IsValidCrashFolder(File dirPath, boolean checkSummary) {
        try {
            if (checkSummary) {
                File summary = new File(dirPath, "crash_report");
                if (!summary.exists()) {
                    return false;
                }
            }
            Long.parseLong(dirPath.getName());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发送数据，并统计发送结果，和是否将剩余文件改名
     *
     * @param rootDir      根目录
     * @param exts         参数
     * @param checkSummary 是否需要检查有crash_report文件
     * @param maxNum       最大上传文件个数
     * @param bDeleted     是否需要删除剩余文件
     * @return 统计结果
     */
    private int[] postCrashFiles(File rootDir, Map<String, String> exts, boolean checkSummary, int maxNum, boolean bDeleted) {
        int uploadCount = 0;
        int failedCount = 0;
        if (rootDir != null && rootDir.exists() && rootDir.isDirectory() && rootDir.listFiles() != null && rootDir.listFiles().length > 0) {
            List<File> logDirs = sort(rootDir.listFiles());
            for (File crashDir : logDirs) {
                try {
                    if (IsValidCrashFolder(crashDir, checkSummary)) {
                        if (uploadCount >= maxNum) {
                            if (bDeleted) {
                                FileUtils.deleteDir(crashDir.getAbsolutePath());
                            }
                            failedCount++;
                            continue;
                        }

                        if (reportCrashDataForm(crashDir, exts) == 0) {
                            uploadCount++;
                            if (!mCrashInterface.isDebugable()) {
                                FileUtils.deleteDir(crashDir.getAbsolutePath());
                            } else {
                                try {
                                    FileUtils.safeRenameTo(crashDir, new File(crashDir.getParentFile(), crashDir.getName() + "_UPLOAD"));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            // 如果上传失败了，检查洗是否是30天以前的log，
                            cleanOldCrashDirectory(crashDir);
                            failedCount++;
                        }
                    } else {
                        if (!(mCrashInterface.isDebugable() && crashDir.getName().endsWith("_UPLOAD"))) {
                            if (crashDir.isDirectory()) {
                                FileUtils.deleteDir(crashDir.getAbsolutePath());
                            } else {
                                FileUtils.deleteFile(crashDir.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
        failedCount += (failedCount == 0 ? 0 : 1);

        if (AppEnv.ISAPPDEBUG) {
            Log.d(TAG, "the result of " + rootDir.getAbsolutePath() + " is\n" + "uploadCount = "
                    + uploadCount + ", failedCount = " + failedCount);
        }
        return new int[]{uploadCount, failedCount};
    }

    /**
     * 发送Java Crash文件
     *
     * @param exts 相关参数
     * @return 发送统计结果
     */
    private int[] postJavaCrashFile(Map<String, String> exts) {
        int uploadCount = 0;
        int failedCount = 0;
        try {
            List<String> javaCrashList = new ArrayList<String>();
            javaCrashList.add(mContext.getFilesDir() + File.separator + CRASH_FOLDER + File.separator);
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                javaCrashList.add(Environment.getExternalStorageDirectory().getPath() + "/360/" + mCrashInterface.getProduct() + File.separator + CRASH_FOLDER + File.separator);
            }

            for (String rootDir : javaCrashList) {
                try {
                    int[] count = postCrashFiles(new File(rootDir), exts, true, 10, true);
                    uploadCount += count[0];
                    failedCount += count[1];
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        return new int[]{uploadCount, failedCount};
    }

    /**
     * 发送Native Crash文件
     *
     * @param exts 相关参数
     * @return 统计结果
     */
    private int[] postNativeCrashFile(Map<String, String> exts) {

        int uploadCount = 0;
        int failedCount = 0;
        try {
            List<String> nativeCrashList = new ArrayList<String>();
            nativeCrashList.add(mContext.getFilesDir() + File.separator + NATIVE_CRASH_FOLDER + File.separator);
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                nativeCrashList.add(Environment.getExternalStorageDirectory().getPath() + "/360/" + mCrashInterface.getProduct() + File.separator + NATIVE_CRASH_FOLDER + File.separator);
            }

            for (String rootDir : nativeCrashList) {
                try {
                    int[] count = postCrashFiles(new File(rootDir), exts, true, 10, true);
                    uploadCount += count[0];
                    failedCount += count[1];
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        return new int[]{uploadCount, failedCount};
    }

    /**
     * 发送Breakpad捕获的日志
     *
     * @param exts 参数
     * @return 发送结果
     */
    private int[] postBreakpadCaughtCrashFile(Map<String, String> exts) {
        int uploadCount = 0;
        int failedCount = 0;
        try {
            List<String> breakpadRootDirList = new ArrayList<String>();
            breakpadRootDirList.add(mContext.getFilesDir() + File.separator + DEFAULT_NATIVE_CRASH_FOLDER + File.separator);
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                breakpadRootDirList.add(Environment.getExternalStorageDirectory().getPath() + File.separator + "360"
                        + File.separator + mCrashInterface.getProduct() + File.separator + DEFAULT_NATIVE_CRASH_FOLDER
                        + File.separator);
            }

            for (String rootDir : breakpadRootDirList) {
                try {
                    int[] count = postFilesUnderBreakpadRootDir(new File(rootDir), exts, true, BREAKPAD_MAX_UPLOAD_FILES, true);
                    uploadCount += count[0];
                    failedCount += count[1];
                    if (AppEnv.ISAPPDEBUG) {
                        Log.i(TAG, "bk: rootDir = " + rootDir + " \nuploadCount = " + uploadCount + " failedCount = " + failedCount);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        return new int[]{uploadCount, failedCount};
    }

    private int[] postFilesUnderBreakpadRootDir(File rootDir, Map<String, String> exts, boolean checkSummary, int maxNum, boolean bDeleted) {
        if (AppEnv.ISAPPDEBUG) {
            Log.d(TAG, "postFilesUnderBreakpadRootDir: rootDir" + rootDir.getAbsolutePath());
        }
        int uploadCount = 0, failedCount = 0;
        do {
            if (rootDir.exists() && rootDir.isDirectory()) {
                File[] subFiles = rootDir.listFiles();
                if (subFiles == null || subFiles.length == 0) {
                    if (AppEnv.ISAPPDEBUG) {
                        Log.d(TAG, "has no file under directory " + rootDir.getAbsolutePath());
                    }
                    break;
                }

                //检查是否有crashreport,若基本信息都没有收集到的话，不好统计崩溃人数等，干脆全删掉
                File summaryFile = new File(rootDir, ICrashCollector.SUMMARY_FILE);
                if (checkSummary && (!summaryFile.exists() || summaryFile.isDirectory())) {
                    if (AppEnv.ISAPPDEBUG) {
                        Log.d(TAG, "There is no " + ICrashCollector.SUMMARY_FILE + " under " + rootDir.getAbsolutePath());
                    }
                    clearAllDumpFiles(rootDir);
                    break;
                }

                List<File> fileList = sortFileByLastModifiedTime(subFiles); //按创建时间降序排序

                int[] result = classifyAndPostDumpFiles(rootDir.getAbsolutePath(), fileList, exts,
                        maxNum, bDeleted);
                uploadCount = result[0];
                failedCount = result[1];
                break;
            }
        } while (false);

        return new int[]{uploadCount, failedCount};
    }

    /**
     * 删除根目录下所有的文件
     *
     * @param rootPathFile 根目录文件
     */
    private void clearAllDumpFiles(File rootPathFile) {
        if (rootPathFile.exists()) {
            for (File file : rootPathFile.listFiles()) {
                if (file.isDirectory()) {
                    FileUtils.deleteDir(file.getAbsolutePath());
                } else {
                    FileUtils.deleteFile(file);
                }
            }
        }
    }

    /**
     * 按照文件修改时间将降序排列
     *
     * @param files 文件数组
     * @return 结果
     */
    private List<File> sortFileByLastModifiedTime(File[] files) {
        List<File> fileList = Arrays.asList(files);
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                if (lhs.lastModified() > rhs.lastModified()) {
                    return -1;
                } else if (lhs.lastModified() < rhs.lastModified()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        if (AppEnv.ISAPPDEBUG) {
            Log.d(TAG, "after sort is");
            for (File file : fileList) {
                Log.d(TAG, file.getName() + ": last modified time " + file.lastModified());
            }
            Log.d(TAG, "sortFileByLastModifiedTime: end");
        }

        return fileList;
    }


    /**
     * 将相同相同前缀的dump文件拷贝到相同的目录里，其余的全部删除
     *
     * @param rootDir   根目录
     * @param dumpFiles dump文件列表
     * @param bDeleted  是否应该删除
     * @return 成功和失败的个数
     */
    private int[] classifyAndPostDumpFiles(String rootDir, List<File> dumpFiles, Map<String, String> exts,
                                           int maxNum, boolean bDeleted) {
        int uploadCount = 0;
        int failedCount = 0;
        ArrayList<String> uploadedFileList = new ArrayList<String>();
        try {
            File crashSummaryFile = new File(rootDir, ICrashCollector.SUMMARY_FILE);
            Pattern pattern = Pattern.compile("^[0-9a-zA-Z\\-]*\\.dmp");
            Matcher matcher;
            for (File file : dumpFiles) {
                if (uploadCount >= maxNum) {
                    break;
                }

                if (uploadedFileList.contains(file.getName())) {
                    if (AppEnv.ISAPPDEBUG) {
                        Log.d(TAG, "classifyAndPostDumpFiles: continue. files.getName() = " + file.getName());
                    }
                    continue;
                }

                matcher = pattern.matcher(file.getName());
                if (file.exists() && !file.getName().equals(ICrashCollector.SUMMARY_FILE)
                        && file.isFile() && matcher.find()) {
                    String dumpPrefix = matcher.group().replace(BREAKPAD_DUMP_SUFFIX, "");

                    if (AppEnv.ISAPPDEBUG) {
                        Log.d(TAG, "file name is " + file.getAbsolutePath() + "\n" + "dumpPrefix = " + dumpPrefix);
                    }

                    ArrayList<File> fileArrayInSameCrash = new ArrayList<File>();

                    File dumpFile = new File(rootDir, dumpPrefix + BREAKPAD_DUMP_SUFFIX);
                    if (dumpFile.exists() && dumpFile.isFile()) {
                        fileArrayInSameCrash.add(dumpFile);
                        uploadedFileList.add(dumpFile.getName());
                    }

                    File dumpInfoFile = new File(rootDir, dumpPrefix + BREAKPAD_DUMP_INFO_SUFFIX);
                    if (dumpInfoFile.exists() && dumpInfoFile.isFile()) {
                        fileArrayInSameCrash.add(dumpInfoFile);
                        uploadedFileList.add(dumpInfoFile.getName());
                    }

                    File dumpLogFile = new File(rootDir, dumpPrefix + BREAKPAD_DUMP_LOG_SUFFIX);
                    if (dumpLogFile.exists() && dumpLogFile.isFile()) {
                        fileArrayInSameCrash.add(dumpLogFile);
                        uploadedFileList.add(dumpLogFile.getName());
                    }

                    if (crashSummaryFile.exists()) {
                        fileArrayInSameCrash.add(crashSummaryFile);
                    }

                    if (AppEnv.ISAPPDEBUG) {
                        Log.d(TAG, "fileArrayInSameCrash is");
                        for (File sortedFile : fileArrayInSameCrash) {
                            Log.d(TAG, sortedFile.getAbsolutePath());
                        }
                    }

                    int result = reportCrashDataForm(fileArrayInSameCrash, exts);
                    if (AppEnv.ISAPPDEBUG) {
                        Log.d(TAG, "rootDir = " + rootDir + ", result = " + result);
                    }
                    if (result == 0) {
                        uploadCount++;
                    } else {
                        failedCount++;
                    }
                }
            }
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
            failedCount++;
        }

        if (bDeleted) {
            clearAllDumpFiles(new File(rootDir));
        }
        if (AppEnv.ISAPPDEBUG) {
            Log.d(TAG, "classifyAndPostDumpFiles: rootDir = " + rootDir + "\nuploadCount = "
                    + uploadCount + ", failedCount " + failedCount);
        }

        return new int[]{uploadCount, failedCount};
    }


    /**
     * Java、Native和Breakpad捕获的日志全部上传，主要由GuardService静默上传和在wifi情况下用户点击上传调用
     * 由于网络变化时，有些机型可能收到两次广播，起两个进程执行这条命令，造成多进程的问题，所以使用synchronized关键字进行同步
     *
     * @param exts              参数
     * @param uploadOnNewThread 是否需要专门起一个线程做上传
     * @return 上传结果
     */
    public synchronized int checkUploadCrashFile(Map<String, String> exts, boolean uploadOnNewThread) {
        ProcessLock pl = new ProcessLock(mContext, "crash_upload", true);
        try {
            int result = 0;
            if (pl.tryLock(1, 0, false)) {
                if (AppEnv.ISAPPDEBUG) {
                    Log.d(TAG, "ready to upload crash file, uploadOnNewThread=" + uploadOnNewThread);
                }
                result = dealWithUploadTask(exts, uploadOnNewThread);
            } else {
                result = -1;
                if (AppEnv.ISAPPDEBUG) {
                    Log.d(TAG, "another crash process is uploading file, so we skip it");
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pl.freeLock();
        }
        return 0;
    }


    private synchronized int dealWithUploadTask(Map<String, String> exts, boolean uploadOnNewThread) {
        int uploadResult = 0;
        try {
            UploadThread uploadThread = new UploadThread(exts, "CrashUploadThread");
            if (uploadOnNewThread) {
                uploadThread.start();
                uploadThread.join();
            } else {
                uploadThread.run();
            }
            uploadResult = uploadThread.getUploadResult();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uploadResult;
    }

    /**
     * 只在用户点击上传的非wifi状态会调用
     *
     * @param rootDir 根目录
     * @param exts    相关参数
     * @param maxNum  最大上传个数
     * @return 上传结果
     */
    public synchronized int checkUploadCrashFile(File rootDir, Map<String, String> exts, int maxNum) {
        ProcessLock pl = new ProcessLock(mContext, "crash_upload", true);
        try {
            int result = 0;
            if (pl.tryLock(1, 0, false)) {
                if (AppEnv.ISAPPDEBUG) {
                    Log.d(TAG, "ready to upload crash file " + rootDir.getAbsolutePath());
                }
                int[] count = postCrashFiles(rootDir, exts, true, maxNum, false);
                result = count[0];
                if (count[0] == 0 && count[1] == 0) {
                    result = -1; // 没有失败上传,我们也认为是成功
                }
            } else {
                result = -1;
                if (AppEnv.ISAPPDEBUG) {
                    Log.d(TAG, "another crash process is uploading file, so we skip it");
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pl.freeLock();
        }
        return 0;
    }

    private boolean cleanOldCrashDirectory(File crashDir) {
        String timeStamp = crashDir.getName();
        try {
            Long dirTime = Long.valueOf(timeStamp);
            if (Math.abs(System.currentTimeMillis() - dirTime.longValue()) > (MAX_CRASH_DIR_EXIST_TIME)) {
                FileUtils.deleteDir(crashDir.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            FileUtils.deleteDir(crashDir.getAbsolutePath());
            return true;
        }
        return false;
    }

    private long getSDCardFreeStorage() {
        File sdcard = Environment.getExternalStorageDirectory();
        if (sdcard.exists()) {
            File file = new File(sdcard.getPath());
            StatFs statFs = new StatFs(file.getPath());
            return statFs.getBlockSize() / 1024 * statFs.getAvailableBlocks();
        }
        return 0;
    }
}
