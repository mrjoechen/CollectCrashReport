package tech.jcjc.crashcollection.crashupload;

import android.content.Context;
import android.util.Log;


import java.io.File;
import java.util.Map;

import tech.jcjc.crashcollection.AppEnv;
import tech.jcjc.crashcollection.interfaces.ICrashInterface;


public class UploadByDirTask {
    private static final String TAG = "CrashCheckUpload";

    private Context mContext;

    private ICrashInterface mCrashInterface;

    private int mUploadResult;

    private final File mCrashRootDir;

    private final Map<String, String> mParams;

    public UploadByDirTask(Context context, ICrashInterface crashInterface, File rootDir, Map<String, String> ex) {
        mContext = context;
        mCrashInterface = crashInterface;
        mCrashRootDir = rootDir;
        mUploadResult = -1;
        mParams = ex;
    }


    public int doUpload() {
        if (mCrashRootDir.exists() && mCrashRootDir.isDirectory()) {
            UploadAction upload = new UploadAction(mContext, mCrashInterface);
            mUploadResult = upload.StartZipAndUploadByFileName(mCrashRootDir, mParams);
            if (AppEnv.ISAPPDEBUG) {
                Log.i(TAG, "upload crash file:" + mCrashRootDir + " result:" + String.valueOf(mUploadResult));
            }
        }

        return mUploadResult;
    }
}
