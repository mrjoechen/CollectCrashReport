
package tech.jcjc.crashcollection.crashupload;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import tech.jcjc.crashcollection.AppEnv;
import tech.jcjc.crashcollection.collector.BaseInfoCollector;
import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.FileUtils;
import tech.jcjc.crashcollection.utils.SecurityUtil;

public class UploadAction {
    private static final String TAG = AppEnv.ISAPPDEBUG ? "UploadAction" : UploadAction.class.getSimpleName();

    public static final int ERR_FILE_NOT_FOUND = -2000;

    public static final int ERR_FILE_TO_BIG = -2001;

    private static final String TEMP_PATH = "dump";

    private static final String TEMP_FILENAME = "dump.cache";

    private static final String UPLOAD_FILENAME = "f2u.tmp";

    private static final int MAX_FILE_SIZE = 1024 * 1024 * 2;

    private static final int MAX_DIR_SIZE = 1024 * 1024 * 5;

    private final String mFileToUpload;

    private final Context mContext;

    private static final String CRASH_KEY = "CrashDump";

    private static final String CRASH_INFO_FILE = "crash_report";

    private static final String QUERY_UPLOAD_HTTP_URL = "http://scan.call.f.360.cn/HarassingCallQueryJson";

    private final ICrashInterface crashInterface;

    public UploadAction(Context context, ICrashInterface c) {
        mContext = context;
        crashInterface = c;
        File folder = mContext.getFileStreamPath(TEMP_PATH);
        if (FileUtils.makeSurePathExists(folder)) {
            mFileToUpload = FileUtils.pathAppend(folder.getAbsolutePath(), UPLOAD_FILENAME);
        } else {
            mFileToUpload = mContext.getFileStreamPath(TEMP_FILENAME).getAbsolutePath();
        }
    }

    public int StartZipAndUploadByFileName(File dirToBeZip, Map<String, String> exts) {
        long errorCode = 0;
        File file2upload = null;
        try {
            File uploadDir = new File(dirToBeZip.getAbsolutePath());
            if (uploadDir != null && uploadDir.isDirectory() && uploadDir.list(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String filename) {
                    return CRASH_INFO_FILE.equals(filename);
                }
            }).length == 1) {
                // 压缩摘要文件
                
//                byte[] data = ZipUtil.GZip(FileUtils.readFileByte(new File(dirToBeZip.getAbsolutePath() + "/crash_report")));
//                JSONObject jsonString = new JSONObject();
//                if (exts != null && !exts.isEmpty()) {
//                    Iterator<?> iter = exts.entrySet().iterator();
//                    while (iter.hasNext()) {
//                        Map.Entry entry = (Map.Entry) iter.next();
//                        String key = (String) entry.getKey();
//                        String val = (String) entry.getValue();
//                        if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(val)) {
//                            jsonString.put(key, val);
//                        }
//                    }
//                }
//                jsonString.put("file", base64Encode(data));
//                String[] fileList = uploadDir.list();
//                if (fileList != null && fileList.length >= 1) {
//                    try {
//                        File file2upload = new File(mFileToUpload);
//                        file2upload.delete();
//                        ZipUtil.SizeLimitZipResult zret = ZipUtil.ZipDirGzip(dirToBeZip, file2upload, MAX_FILE_SIZE, MAX_DIR_SIZE);
//                        if (zret == ZipUtil.SizeLimitZipResult.SizeLimitZipResult_OK && file2upload.exists()) {
//                            byte[] dataFile = FileUtils.readFileByte(file2upload);
//                            file2upload.delete();
//                            jsonString.put("data", base64Encode(dataFile));
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                jsonString.put("version", crashInterface.getVersion());
//                jsonString.put("product", crashInterface.getProduct());
//                jsonString.put("debug", String.valueOf(crashInterface.isDebugable()));

//                errorCode = postDataToCrashServer(jsonString.toString());
                
                // 压缩上传文件到服务器
                
                // 取出CrashHash
                String[] crashHashFileList = uploadDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(BaseInfoCollector.CRASH_HASH_TMP_FILE_SUF);
                    }
                });
                if (crashHashFileList.length != 1) {
                    return -99;
                }

                String crashHash = crashHashFileList[0];
                int dot = crashHash.indexOf(BaseInfoCollector.CRASH_HASH_TMP_FILE_SUF);
                crashHash = crashHash.substring(0, dot).trim();

                if ("".equals(crashHash)) {
                    return -99;
                }

                FilenameFilter hashFileOutFilter = new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return !name.endsWith(BaseInfoCollector.CRASH_HASH_TMP_FILE_SUF);
                    }
                };
                String[] fileList = uploadDir.list(hashFileOutFilter);
                
                if (fileList != null && fileList.length >= 1) {
                    try {
                        file2upload = new File(mFileToUpload);
                        file2upload.delete();
                        ZipUtil.SizeLimitZipResult zret = ZipUtil.ZipDir(dirToBeZip, file2upload, MAX_FILE_SIZE, MAX_DIR_SIZE, hashFileOutFilter);
                        if (zret == ZipUtil.SizeLimitZipResult.SizeLimitZipResult_OK && file2upload.exists()) {
                            String obfuscatedKey = crashInterface.getEncryptKey();
                            // 需要加密
                            if (obfuscatedKey != null && !"".equals(obfuscatedKey)) {
                                String key = deObfuscateKey(obfuscatedKey);

                                File encryptedFile = new File(mFileToUpload + "_encrypted");
                                
                                if (SecurityUtil.AES_encrypt(file2upload, key, encryptedFile)) {
                                    file2upload.delete();
                                    file2upload = encryptedFile;
                                } else {
                                    // 加密错误
                                    return -99;
                                }
                            }
                            return (int) uploadDataToFileServer(crashHash, file2upload);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
            }
            return (int) errorCode;

        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                Log.e(TAG, "", e);
            }
        } finally {
            if (file2upload != null) {
                file2upload.delete();
            }
        }

        return -99;

    }


    private static final String XOR_SRC = "C3l7SSEAtAqBGsLaRLABn28a28BIMD7gT9jl3dINWos2pxqyeznLKpynIwf801Ha0Cv8o94UlQlIBIa7NXazZ3wB3NFsGfrTIDSimOr35gXVi7CZkDFca99loQdLP3OW20stwWgyZyPvnCFoqYwzLaCWZeeuGDyZrMMlg33TXkhowRifWIBY1aI0ElpqwkQFiPAE8KGWJP05F99vJikb6XK7Uiz9LOwZzCwJNTFh5MdX4HQJiKhiTezkNFx268IaWgcdj8jTq6CboxIAUWe8diyDvNDqRju94mqpdvUfODKFdPQStmwC8H8oBmSXOYbqTCyaUtU9vmgl070n5ApqLqQ0u34mbSOrHtmCep05wZd6rJ9LPiTr94SNJNODq9t5HBn0S4UGakw3THLx2oQROYCNUToRaOoBNy8JGDdVU5WIbZ1tHlpEKLxHNvjxY0FYnfshIlrArnMG2wDdubCAj385QklKo977uI1uLjWscpvoSNPUB9PZEO5ibn6H6kYl";
    private static final byte[] XOR_SRC_BUF = XOR_SRC.getBytes();
    private static final String MIX = "bSnNz94hok3GI4Ug2m7s";
    private static final String deObfuscateKey(String in) {
        if (in == null || "".equals(in)) {
            return "";
        }

        byte[] tmpBuf = Base64.decode(in);
        final int len = tmpBuf.length;
        byte[] mixedBuf = new byte[len];

        for (int i = len - 1, j = 0; i >= 0; i--, j++) {
            mixedBuf[i] = (byte) (tmpBuf[j] ^ XOR_SRC_BUF[j]);
        }
        String tmpStr = new String(mixedBuf);

        return tmpStr.substring(0, 1) + tmpStr.substring(MIX.length() + 1);
    }


    /**
     * 根据传入的文件列表，进行压缩上传
     *
     * @param fileList 文件列表
     * @param exts     附加参数
     * @return 是否成功
     */
    public int StartZipAndUploadByFileList(List<File> fileList, Map<String, String> exts) {
        long errorCode = 0;
        try {
            File summaryFile = null;
            for (File file : fileList) {
                if (CRASH_INFO_FILE.equals(file.getName())) {
                    summaryFile = file;
                    break;
                }
            }

            if (FileUtils.makeSureFilesExist(fileList) && summaryFile != null && summaryFile.exists()
                    && summaryFile.isFile()) {
                byte[] summaryFileData = ZipUtil.GZip(FileUtils.readFileByte(summaryFile));
                JSONObject jsonString = new JSONObject();
                if (exts != null && !exts.isEmpty()) {
                    Iterator<?> iter = exts.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry entry = (Map.Entry) iter.next();
                        String key = (String) entry.getKey();
                        String value = (String) entry.getValue();

                        if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                            jsonString.put(key, value);
                        }
                    }
                }
                jsonString.put("file", base64Encode(summaryFileData));

                try {
                    File file2Upload = new File(mFileToUpload);
                    file2Upload.delete();

                    File[] files = new File[fileList.size()];
                    for (int i = 0; i < fileList.size(); i++) {
                        files[i] = fileList.get(i);
                    }

                    ZipUtil.SizeLimitZipResult zipResult = ZipUtil.zipFileListGzip(files, file2Upload, MAX_FILE_SIZE, MAX_DIR_SIZE);

                    if (zipResult == ZipUtil.SizeLimitZipResult.SizeLimitZipResult_OK && file2Upload.exists()) {
                        byte[] dataFile = FileUtils.readFileByte(file2Upload);
                        file2Upload.delete();
                        jsonString.put("data", base64Encode(dataFile));
                    }
                } catch (Exception e) {
                    if (AppEnv.ISAPPDEBUG) {
                        e.printStackTrace();
                    }
                }

                jsonString.put("version", crashInterface.getVersion());
                jsonString.put("product", crashInterface.getProduct());
                jsonString.put("debug", String.valueOf(crashInterface.isDebugable()));

                errorCode = postDataToCrashServer(jsonString.toString());
            }

            return (int) errorCode;
        } catch (Exception e) {
            if (AppEnv.ISAPPDEBUG) {
                e.printStackTrace();
            }
        }
        return -99;
    }

    

    /**
     * 像服务器发送JSON数据
     *
     * @param jsonData 数据
     * @return 结果
     */
    public long postDataToCrashServer(String jsonData) {
        long errorCode = -1;

        try {
            JSONObject requestMessage = new JSONObject();
            requestMessage.put("req_id", String.valueOf(0));
            requestMessage.put("mid", SystemUtil.getMid2(mContext));
            requestMessage.put("imei", SystemUtil.getImei(mContext));
            requestMessage.put("product", crashInterface.getProduct());
            requestMessage.put("combo", CRASH_KEY);
            requestMessage.put("uv", 1);
            requestMessage.put("debug", false);
            requestMessage.put("client_version", crashInterface.getVersion());
            requestMessage.put("phone_call_query", new JSONObject().put("is_upload", true));

            JSONArray pairs = new JSONArray();
            JSONObject kv = new JSONObject();
            kv.put("key", CRASH_KEY);
            kv.put("val", jsonData);
            pairs.put(kv);
            requestMessage.put("exts", pairs);
            // TODO yjl 添加上传代码
//            ProtocolRequest request = new ProtocolRequest(QUERY_UPLOAD_HTTP_URL, null, false);
//            RequestResult checkResult = request.query(requestMessage.toString().getBytes());
//            if (checkResult != null && checkResult.mData != null) {
//                JSONObject result = new JSONObject(new String(checkResult.mData, "UTF-8"));
//                errorCode = result.getInt("error_code");
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return errorCode;
    }

    /**
     * 将压缩后的文件上传到文件服务器
     * @param crashHash
     * @param fileToUpload
     * @return
     */
    public long uploadDataToFileServer(String crashHash, File fileToUpload) {
        ICrashInterface.IUploader uploader = crashInterface.getUploader();
        if (uploader == null) {
            return -1;
        }
        return uploader.doUpload(crashHash, fileToUpload);
    }

    private static char[] base64EncodeChars = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    public static String base64Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int len = data.length;
        int i = 0;

        while (i < len) {
            int b1 = data[(i++)] & 0xFF;
            if (i == len) {
                sb.append(base64EncodeChars[(b1 >>> 2)]);
                sb.append(base64EncodeChars[((b1 & 0x3) << 4)]);
                sb.append("==");
                break;
            }
            int b2 = data[(i++)] & 0xFF;
            if (i == len) {
                sb.append(base64EncodeChars[(b1 >>> 2)]);
                sb.append(base64EncodeChars[((b1 & 0x3) << 4 | (b2 & 0xF0) >>> 4)]);
                sb.append(base64EncodeChars[((b2 & 0xF) << 2)]);
                sb.append("=");
                break;
            }
            int b3 = data[(i++)] & 0xFF;
            sb.append(base64EncodeChars[(b1 >>> 2)]);
            sb.append(base64EncodeChars[((b1 & 0x3) << 4 | (b2 & 0xF0) >>> 4)]);
            sb.append(base64EncodeChars[((b2 & 0xF) << 2 | (b3 & 0xC0) >>> 6)]);
            sb.append(base64EncodeChars[(b3 & 0x3F)]);
        }
        return sb.toString();
    }
}
