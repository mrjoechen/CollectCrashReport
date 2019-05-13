
package tech.jcjc.crashcollection.interfaces;

import java.io.File;
import java.util.Map;

public interface ICrashInterface {

    public enum ExceptionAction {
        emDefault, emSkip, emCollector, emHideCollector, emRestart,
    }
    
    interface IUploader {
        long doUpload(String crashHash, File file);
    }

    String getVersion();

    String getProduct();

    boolean isDebugable();

    Map<String, String> baseInfoCrashCollector(int type, Thread thread, Object ex);

    Map<String, String> objectInfoCrashCollector(int type, Thread thread, Object ex);

    Map<String, String> customInfoCrashCollector(int type, Thread thread, Object ex);

    void uncaughtExceptionPreHandler(int type, Thread thread, Object ex);

    void uncaughtExceptionResultHandler(int type, String crashRoot, ExceptionAction action, Thread thread, Object ex);

    void crashUploadResultHandler(int[] javaResult, int[] nativeResult, int[] breakpadResult);

    boolean shouldRunHandler(int type, Thread thread, Object ex);

    ExceptionAction getCrashCollectorType(int type, Thread thread, Object ex);

    String getCrashRootFolder(int type, Thread thread, Object ex);

    /**
     * 获取一个 {@link IUploader}对象用来上传报告
     * @return
     */
    IUploader getUploader();

    /**
     * 获取用于加密的key，若返回null，表示不需要加密，否则按照约定的方式处理后作为加密的key
     * @return
     */
    String getEncryptKey();
}
