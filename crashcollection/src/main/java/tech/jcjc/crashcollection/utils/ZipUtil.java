package tech.jcjc.crashcollection.utils;


import android.util.Log;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import tech.jcjc.crashcollection.AppEnv;

public class ZipUtil {
    private static final String TAG = "ZipUtil";

    public static final boolean BEDEBUG = AppEnv.ISAPPDEBUG;
    private static final int BUFF_SIZE = 1024 * 32; // 32k Byte

    public enum SizeLimitZipResult {
        SizeLimitZipResult_OK, // 正常压缩
        SizeLimitZipResult_TooBig, // 有超出体检的压缩文件
        SizeLimitZipResult_NotFound // 目录是空的
    }

    ;

    /**
     * 用标准zip压缩一个目录
     *
     * @param dirTobeZip 待压缩目录
     * @param newZipFile 压缩后的文件
     * @throws IOException
     */
    public static void ZipDir(File dirTobeZip, File newZipFile) throws IOException {

        InputStream input = null;

        ZipOutputStream zipOut = null;

        zipOut = new ZipOutputStream(new FileOutputStream(newZipFile));
        if (dirTobeZip.isDirectory()) {
            // 判断是否是目录
            File lists[] = dirTobeZip.listFiles();
            // 列出全部文件
            for (File currentFile : lists) {
                input = new FileInputStream(currentFile);
                zipOut.putNextEntry(new ZipEntry(dirTobeZip.getName() + File.separator + currentFile.getName()));
                int temp = 0;
                // 接收输入的数据
                while ((temp = input.read()) != -1) { // 读取内容
                    zipOut.write(temp); // 压缩输出内容
                }
                input.close();
            }
        }
        zipOut.close();
    }

    /**
     * 使用gzip格式压缩一个文件
     *
     * @param srcFile 待压缩文件
     * @param zipFile 压缩后的文件
     * @throws IOException
     */
    public static void GzipOneFile(File srcFile, File zipFile) throws IOException {
        if (srcFile.exists()) {
            GZIPOutputStream zipout = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile),
                    BUFF_SIZE));
            _gzipFile(srcFile, zipout);
            zipout.close();
        }
    }

    public static SizeLimitZipResult ZipDir(File dirTobeZip, File newGZipFile, int fileSizeLimit, int totalSizeLimit, FilenameFilter filenameFilter) throws IOException {
        String tempFileName = newGZipFile.getAbsolutePath() + ".tmp";
        if (BEDEBUG) {
            Log.i(TAG, "tempFileName:" + tempFileName);
        }
        File tempZipFile = new File(tempFileName);
        boolean dret1 = tempZipFile.delete(); // 避免出现无法解压缩的情况
        if (BEDEBUG) {
            Log.i(TAG, "delete " + tempZipFile + " dret1:" + dret1);
        }
        SizeLimitZipResult ret = zipDirWithSizeLimit(dirTobeZip, tempZipFile, fileSizeLimit, totalSizeLimit, filenameFilter);

        if (ret == SizeLimitZipResult.SizeLimitZipResult_NotFound) {
            if (BEDEBUG) {
                Log.e(TAG, "dret:" + ret);
            }
            return ret;
        }

        FileUtils.copyFile(tempZipFile, newGZipFile);
        @SuppressWarnings("unused")
        boolean dret = tempZipFile.delete();
        if (BEDEBUG) {
            Log.i(TAG, "delete " + tempZipFile + " ret:" + ret);
        }

        return ret;
    }


    /**
     * 用标准zip 把一个目录压缩成一个文件，再用GZip再压缩一下 注意 newGZipFile 不要放在 dirTobeZip 目录下
     *
     * @param dirTobeZip  代压缩的目录
     * @param newGZipFile 压缩后的文件
     * @throws IOException
     */
    public static SizeLimitZipResult ZipDirGzip(File dirTobeZip, File newGZipFile, int fileSizeLimit, int totalSizeLimit)
            throws IOException {
        return ZipDirGzip(dirTobeZip, newGZipFile, fileSizeLimit, totalSizeLimit, null);
    }

    /**
     * 用标准zip 把一个目录压缩成一个文件，再用GZip再压缩一下 注意 newGZipFile 不要放在 dirTobeZip 目录下。可选参数filenameFilter用来过滤加入压缩的文件
     *
     * @param dirTobeZip  代压缩的目录
     * @param newGZipFile 压缩后的文件
     * @param filenameFilter 用于过滤加入压缩的文件
     * @throws IOException
     */
    public static SizeLimitZipResult ZipDirGzip(File dirTobeZip, File newGZipFile, int fileSizeLimit, int totalSizeLimit, FilenameFilter filenameFilter)
            throws IOException {
        String tempFileName = newGZipFile.getAbsolutePath() + ".tmp";
        if (BEDEBUG) {
            Log.i(TAG, "tempFileName:" + tempFileName);
        }
        File tempZipFile = new File(tempFileName);
        boolean dret1 = tempZipFile.delete(); // 避免出现无法解压缩的情况
        if (BEDEBUG) {
            Log.i(TAG, "delete " + tempZipFile + " dret1:" + dret1);
        }
        SizeLimitZipResult ret = zipDirWithSizeLimit(dirTobeZip, tempZipFile, fileSizeLimit, totalSizeLimit, filenameFilter);

        if (ret == SizeLimitZipResult.SizeLimitZipResult_NotFound) {
            if (BEDEBUG) {
                Log.e(TAG, "dret:" + ret);
            }
            return ret;
        }

        GzipOneFile(tempZipFile, newGZipFile);
        @SuppressWarnings("unused")
        boolean dret = tempZipFile.delete();
        if (BEDEBUG) {
            Log.i(TAG, "delete " + tempZipFile + " ret:" + ret);
        }

        return ret;
    }


    /***
     * 将一个文件列表压缩成一个文件，然后用Gzip再次压缩一次
     *
     * @param files          文件列表
     * @param newGZipFile    最终目标
     * @param fileSizeLimit  文件大小
     * @param totalSizeLimit 总体文件大小
     * @return 压缩结果
     */
    public static SizeLimitZipResult zipFileListGzip(File[] files, File newGZipFile, long fileSizeLimit,
                                                     long totalSizeLimit) {
        SizeLimitZipResult ret = SizeLimitZipResult.SizeLimitZipResult_NotFound;
        try {
            String tempFileName = newGZipFile.getAbsolutePath() + ".tmp";
            if (BEDEBUG) {
                Log.i(TAG, "tempFileName:" + tempFileName);
            }
            File tempZipFile = new File(tempFileName);
            boolean dret1 = tempZipFile.delete(); // 避免出现无法解压缩的情况
            if (BEDEBUG) {
                Log.i(TAG, "delete " + tempZipFile + " dret1:" + dret1);
            }
            ret = zipFileArrayWithSizeLimit(files, tempZipFile, fileSizeLimit, totalSizeLimit);

            if (ret == SizeLimitZipResult.SizeLimitZipResult_NotFound) {
                if (BEDEBUG) {
                    Log.e(TAG, "dret:" + ret);
                }
                return ret;
            }

            GzipOneFile(tempZipFile, newGZipFile);
            boolean dret = tempZipFile.delete();
            if (BEDEBUG) {
                Log.i(TAG, "delete " + tempZipFile + " ret:" + ret);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ret;
    }


    /**
     * 先Gzip然后unzip
     *
     * @param srcFile
     * @param destFile
     */
    public static void unGzipZip(final File srcFile, final File destFile) {
        try {
            // unGzip
            final File destFileTmp = new File(srcFile.getAbsolutePath() + ".tmp");
            unGzipFile(srcFile, destFileTmp);

            // unzip
            unZip(destFileTmp, destFile);
        } catch (Exception e) {
            if (BEDEBUG) {
                Log.i(TAG, "unGzipZip got exception");
            }
        }
    }

    /**
     * Unzip文件或者文件夹
     *
     * @param srcFile
     * @param destFile
     * @throws IOException
     */
    public static void unZip(final File srcFile, final File destFile) throws IOException {
        // unzip
        BufferedOutputStream dest = null;
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(srcFile)));
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            File file = new File(destFile, entry.getName());

            if (entry.isDirectory()) {
                if (!file.exists()) {
                    file.mkdirs();
                    continue;
                }
            } else {
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } else {
                    continue;
                }
            }

            final int buffer = 2048;

            int count;
            byte data[] = new byte[buffer];
            FileOutputStream fos = new FileOutputStream(file);
            dest = new BufferedOutputStream(fos, buffer);
            while ((count = zis.read(data, 0, buffer)) != -1) {
                dest.write(data, 0, count);
            }
            dest.flush();
            dest.close();
        }
        zis.close();
    }

    /**
     * 使用gzip格式压缩一个文件
     *
     * @param resFile 需要压缩的文件（夹）
     * @param zipout  压缩的目的文件
     * @throws FileNotFoundException 找不到文件时抛出
     * @throws IOException           当压缩过程出错时抛出
     */
    private static void _gzipFile(File resFile, GZIPOutputStream zipout) throws FileNotFoundException, IOException {

        byte buffer[] = new byte[BUFF_SIZE];
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(resFile), BUFF_SIZE);

        int realLength;
        while ((realLength = in.read(buffer)) != -1) {
            zipout.write(buffer, 0, realLength);
        }
        in.close();
        zipout.flush();
    }

    /**
     * zipDirWithSizeLimit 压缩一个文件夹，限制每个文件的大小和压缩前的总大小
     * 如果单个文件超出大小则不压缩此文件，如果超出总文件大小则只压缩部分文件。 未压缩的文件会被记录到压缩文件的注释中。
     *
     * @param dirTobeZip     待压缩目录
     * @param newZipFile     压缩后生成的文件名，请确保不在压缩目录下
     * @param fileSizeLimit  压缩前单个文件的大小
     * @param totalSizeLimit 压缩前总的文件大小
     * @param filenameFilter 用于过滤加入压缩的文件
     * @return SizeLimitZipResult
     * @throws IOException
     */
    private static SizeLimitZipResult zipDirWithSizeLimit(File dirTobeZip, File newZipFile, long fileSizeLimit,
                                                          long totalSizeLimit, FilenameFilter filenameFilter) throws IOException {

        SizeLimitZipResult ret = SizeLimitZipResult.SizeLimitZipResult_OK;
        boolean hasFile = false;
        if (dirTobeZip.exists() && dirTobeZip.isDirectory()) {
            File lists[] = dirTobeZip.listFiles(filenameFilter);

            ret = zipFileArrayWithSizeLimit(lists, newZipFile, fileSizeLimit, totalSizeLimit);
        } else {
            ret = SizeLimitZipResult.SizeLimitZipResult_NotFound;
        }

        return ret;
    }


    /**
     * 根据传入的文件数组进行压缩，并对每个文件的大小和总大小有限制
     *
     * @param files          文件数组
     * @param newZipFile     新的压缩文件
     * @param fileSizeLimit  单个文件大小
     * @param totalSizeLimit 总大小
     * @return 压缩结果
     * @throws IOException 可能抛出的异常
     */
    public static SizeLimitZipResult zipFileArrayWithSizeLimit(File[] files, File newZipFile, long fileSizeLimit,
                                                               long totalSizeLimit) throws IOException {
        SizeLimitZipResult ret = SizeLimitZipResult.SizeLimitZipResult_OK;

        if (files == null || files.length <= 0) {
            ret = SizeLimitZipResult.SizeLimitZipResult_NotFound;
        } else {
            InputStream input = null;

            ZipOutputStream zipOut = null;
            FileOutputStream fOut = new FileOutputStream(newZipFile);
            zipOut = new ZipOutputStream(fOut);

            StringBuilder sb = null;
            boolean needSizeLimit = totalSizeLimit > 0 || fileSizeLimit > 0;

            if (needSizeLimit) {
                sb = new StringBuilder();
            }
            int currentSize = 0;
            // 设置文件输入流
            // 每一个被压缩的文件都用ZipEntry表示，需要为每一个压缩后的文件设置
            // 过滤掉超出大小上限的文件。将文件摘要记录到Zip的Common里面。
            for (int i = 0; i < files.length; i++) {
                File currentFile = files[i];
                input = new FileInputStream(currentFile);
                if (needSizeLimit) {
                    int fsize = input.available();
                    sb.append("[").append(i).append("/").append(files.length).append("]");
                    sb.append(currentFile.getName());
                    sb.append("(").append(fsize).append(")");

                    if (fsize > fileSizeLimit) { // 单个文件超出大小
                        sb.append("[TOO BIG !!!]\n");
                        ret = SizeLimitZipResult.SizeLimitZipResult_TooBig;
                        continue;
                    } else {
                        if (currentSize + fsize < totalSizeLimit) {
                            currentSize += fsize;
                            sb.append('\n');
                        } else {
                            sb.append("[Tatol BIG !!!]\n");
                            ret = SizeLimitZipResult.SizeLimitZipResult_TooBig;
                            continue;
                        }
                    }
                }

                zipOut.putNextEntry(new ZipEntry(currentFile.getName()));
                int readLen = 0;
                // 接收输入的数据
                byte[] buf = new byte[1024];
                while ((readLen = input.read(buf, 0, 1024)) != -1) {
                    zipOut.write(buf, 0, readLen);
                }
                zipOut.closeEntry();
                input.close();
            }
            if (needSizeLimit) {
                if (currentSize == 0) { // 如果由于过滤了大文件，一个文件也没有压缩的话写个注释文件进去
                    zipOut.putNextEntry(new ZipEntry("common.txt"));
                    zipOut.write(sb.toString().getBytes()); // 压缩输出内容
                    zipOut.closeEntry();
                }
                zipOut.setComment(sb.toString());
                if (BEDEBUG) {
                    Log.i(TAG, "cm:" + sb.toString());
                }
            }
            zipOut.close();
            fOut.close();
        }

        return ret;
    }

    /**
     * 使用gzip格式压缩数据
     *
     * @param input 待压缩的字节数组
     * @return 压缩后的字节数组
     */
    public static byte[] GZip(byte[] input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream zipOutputSteam = null;
        InputStream inputStream = null;
        try {
            zipOutputSteam = new GZIPOutputStream(baos);
            inputStream = new BufferedInputStream(new ByteArrayInputStream(input), BUFF_SIZE);
            int len;
            byte[] buffer = new byte[4096];
            while ((len = inputStream.read(buffer)) != -1) {
                zipOutputSteam.write(buffer, 0, len);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (zipOutputSteam != null) {
                    zipOutputSteam.close();
                }
                baos.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return baos.toByteArray();
    }

    /**
     * 解压Gzip文件
     *
     * @param srcFile  源文件
     * @param destFile 目的文件
     * @throws IOException
     */
    public static void unGzipFile(File srcFile, File destFile) throws IOException {
        unGzipFile(new FileInputStream(srcFile), destFile);
    }

    /**
     * 解压Gzip文件
     *
     * @param gzipInputStream
     * @param destFile
     * @throws IOException
     */
    public static void unGzipFile(InputStream gzipInputStream, File destFile) throws IOException {
        GZIPInputStream gzis = null;
        FileOutputStream out = null;
        try {
            gzis = new GZIPInputStream(gzipInputStream);
            out = new FileOutputStream(destFile);

            byte[] buf = new byte[1024];
            int len;
            while ((len = gzis.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
        } finally {
            if (gzis != null) {
                try {
                    gzis.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
