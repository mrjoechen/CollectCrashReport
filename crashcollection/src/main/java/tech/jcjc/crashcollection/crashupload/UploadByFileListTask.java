package tech.jcjc.crashcollection.crashupload;

import android.content.Context;
import android.util.Log;
//

import java.io.File;
import java.util.List;
import java.util.Map;

import tech.jcjc.crashcollection.AppEnv;
import tech.jcjc.crashcollection.interfaces.ICrashInterface;
import tech.jcjc.crashcollection.utils.FileUtils;

/**
 * 通过传来的文件列表，来压缩上传各dump文件
 * Created by wangnan on 2016/10/22.
 */

public class UploadByFileListTask {
    private static final String TAG = "CrashCheckUpload";

    private Context mContext;

    private ICrashInterface mCrashInterface;

    private int mUploadResult;

    private final List<File> mFileList;

    private final Map<String, String> mParams;

    public UploadByFileListTask(Context context, ICrashInterface crashInterface, List<File> fileList, Map<String, String> ex) {
        mContext = context;
        mCrashInterface = crashInterface;
        mFileList = fileList;
        mUploadResult = -1;
        mParams = ex;
    }

    public int doUpload() {
        try {
            if (FileUtils.makeSureFilesExist(mFileList)) {
                UploadAction upload = new UploadAction(mContext, mCrashInterface);
                mUploadResult = upload.StartZipAndUploadByFileList(mFileList, mParams);
                if (AppEnv.ISAPPDEBUG) {
                    Log.i(TAG, "fileList is ");
                    for (File file : mFileList) {
                        Log.i(TAG, "*******" + file.getName());
                    }
                    Log.i(TAG, "result = " + mUploadResult);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        return mUploadResult;
    }
}