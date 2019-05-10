
package tech.jcjc.crashcollection.utils;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import tech.jcjc.crashcollection.AppEnv;

public final class SystemUtil {

    private static final boolean DEBUG = AppEnv.ISAPPDEBUG;

    private static final String TAG = "SystemUtils";

    public static final String DEFAULT_IMEI = "360_DEFAULT_IMEI";

    public static final String DEVICE_ID_FILENAME = "DEVICE_ID"; // 3.8.0之前的版本

    public static final String DEVICE_ID_FILENAME_NEW = "DEV"; // 3.8.0及其之后的版本

    public static final String DEVICE_ID_FILENAME_NEW_V2 = "DEVV2"; // 5.0.9及其之后的版本

    public static final String ANDROID_ID_FILENAME = "ANDROID_ID"; // 保存手机的android_id，用来校验DEVICE_ID是否需要重新获取

    public static String getImei(Context ctx) {
        if (ctx != null) {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            try {
                if (tm != null && tm.getDeviceId() != null) {
                    return tm.getDeviceId();
                }
            } catch (Exception e) {
                if (DEBUG) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
        return DEFAULT_IMEI;
    }

    /**
     * 获取Android设备SerialNumber
     *
     * @return "" if no result
     */
    public static synchronized String getSerialNumber() {
        String serialNumber = null;
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            if (clazz != null) {
                Method method = clazz.getMethod("get", String.class, String.class);
                if (method != null) {
                    serialNumber = (String) (method.invoke(clazz, "ro.serialno", ""));
                }
            }
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }

        if (serialNumber == null) {
            return "";
        }

        return serialNumber;
    }

    /**
     * 获取手机当前全球唯一ID， 刷机或者恢复出厂后会变
     *
     * @param context
     * @return never null
     */
    public static synchronized String getDeviceId(Context context) {
        String imei = getImei(context);
        String androidId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        if (androidId == null) {
            androidId = "";
        }

        String serialNumber = getSerialNumber();
        String imei2 = SecurityUtil.getMD5(imei + androidId + serialNumber);
        String deviceId = (imei + "@@" + imei2.substring(8, 24)).toUpperCase();

        return deviceId;
    }

    /**
     * 获取CPU序列号
     *
     * @return CPU序列号(16位) 读取失败为null
     */
    public static String getCPUSerial() {
        String line = "";
        String cpuAddress = null;
        InputStreamReader ir = null;
        LineNumberReader input = null;
        try {
            // 读取CPU信息
            Process pp = Runtime.getRuntime().exec("cat /proc/cpuinfo");
            ir = new InputStreamReader(pp.getInputStream());
            input = new LineNumberReader(ir);
            // 查找CPU序列号
            for (int i = 1; i < 100; i++) {
                line = input.readLine();
                if (line != null) {
                    // 查找到序列号所在行
                    line = line.toLowerCase();
                    int p1 = line.indexOf("serial");
                    int p2 = line.indexOf(":");
                    if (p1 > -1 && p2 > 0) {
                        // 提取序列号
                        cpuAddress = line.substring(p2 + 1);
                        // 去空格
                        cpuAddress = cpuAddress.trim();
                        break;
                    }
                } else {
                    // 文件结尾
                    break;
                }
            }
        } catch (IOException ex) {
            if (DEBUG) {
                ex.printStackTrace();
            }
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ex) {
                    // ignore
                }
            }

            if (ir != null) {
                try {
                    ir.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
        if (DEBUG) {
            Log.d(TAG, "cpuAddress=" + cpuAddress);
        }
        return cpuAddress;
    }

    /*
     * 获取机器Serial号，Android2.3版本以上有效
     */
    public static String getSerial() {
        String serial = null;
        try {
            if (Build.VERSION.SDK_INT >= 9) {
                Class<Build> clazz = Build.class;
                Field field = clazz.getField("SERIAL");
                serial = (String) field.get(null);
                if (serial != null) {
                    serial = serial.toLowerCase();
                }
            }
        } catch (Exception ex) {
            if (DEBUG) {
                ex.printStackTrace();
            }
        }
        if (DEBUG) {
            Log.d(TAG, "serial=" + serial);
        }
        return serial;
    }

    /**
     * 获取ANDROID_ID号，Android2.2版本以上系统有效
     *
     * @return
     */
    public static String getAndroidId(Context context) {
        String androidId = null;
        try {
            if (Build.VERSION.SDK_INT >= 8) {
                androidId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
                if (androidId != null) {
                    androidId = androidId.toLowerCase();
                }
            }
        } catch (Throwable ex) {
            if (DEBUG) {
                Log.e(TAG, ex.toString());
            }
        }
        if (DEBUG) {
            Log.d(TAG, "android_id=" + androidId);
        }
        return androidId;
    }

    /**
     * 获取网卡的MAC地址
     *
     * @param context
     * @return
     */
    public static String getMacAddress(Context context) {
        String macAddress = null;
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifi.getConnectionInfo();
            if (info != null) {
                macAddress = info.getMacAddress();
                if (macAddress != null) {
                    macAddress = macAddress.replaceAll("-", "").replaceAll(":", "").toLowerCase();
                }
            }
        } catch (Exception ex) {
            if (DEBUG) {
                ex.printStackTrace();
            }
        }
        if (DEBUG) {
            Log.d(TAG, "macAddress=" + macAddress);
        }
        return macAddress;
    }

    /**
     * 获取UUID
     *
     * @return
     */
    public static String getUUID() {
        String id = null;
        try {
            id = UUID.randomUUID().toString();
            id = id.replaceAll("-", "").replace(":", "").toLowerCase();
        } catch (Exception ex) {
            if (DEBUG) {
                ex.printStackTrace();
            }
        }
        return id;
    }

    public static String readIdFile(Context context, File idFile, boolean decode) {
        RandomAccessFile f = null;
        String deviceId = null;
        try {
            f = new RandomAccessFile(idFile, "r");
            byte[] bytes = new byte[(int) f.length()];
            f.readFully(bytes);
            if (decode) {
                // deviceId = Utils.DES_decrypt(new String(bytes),
                // context.getPackageName());
            } else {
                deviceId = new String(bytes);
            }
        } catch (Exception ex) {
            //ignore
        } finally {
            if (f != null) {
                try {
                    f.close();
                } catch (Exception ex) {
                    //ignore
                }
            }
        }
        return deviceId;
    }

    // 助手使用了两个标识，m和m2：
    // M的计算方式如下：
    // TelephonyManager tm = (TelephonyManager)
    // context.getSystemService(Context.TELEPHONY_SERVICE);
    // m = Md5Util.md5LowerCase(tm.getDeviceId());
    //
    // m2的计算方式如下：
    public static String getMid2(Context context) {
        String imei = getImei(context);
        String androidId = Settings.System.getString(context.getContentResolver(), "android_id");
        String serialNo = getDeviceSerialForMid2();
        String m2 = SecurityUtil.getMD5("" + imei + androidId + serialNo);
        return m2;
    }

    private static String getDeviceSerialForMid2() {
        String serial = "";
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            serial = (String) get.invoke(c, "ro.serialno");
        } catch (Exception ignored) {
            //ignore
        }
        return serial;
    }

    public static long getMemoryTotalKb() {
        long totalSize = -1L;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/meminfo"));
            String line, totle = null;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    totle = line.split(" +")[1];
                    break;
                }
            }

            totalSize = Long.valueOf(totle);

            return totalSize;
        } catch (Exception e) {
            //ignore
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        return totalSize;
    }

    /** @return 返回内存占用的百分比，0-100 */
    public static int getMemoryUsedPercent() {
        long totalSize = getMemoryTotalKb();
        long freeSize = getMemoryFreeKb();

        if (totalSize > 0 && freeSize > 0) {
            return (int) ((totalSize - freeSize) * 100 / totalSize);
        } else {
            return 0;
        }
    }

    public static long getMemoryFreeKb() {
        long freeSize = -1L;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/meminfo"));
            String line, buff = null, cache = null, free = null;
            int count = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemFree")) {
                    count++;
                    free = line.split(" +")[1];
                    if (count >= 3) {
                        break;
                    }
                } else if (line.startsWith("Buffers")) {
                    count++;
                    buff = line.split(" +")[1];
                    if (count >= 3) {
                        break;
                    }
                } else if (line.startsWith("Cached")) {
                    count++;
                    cache = line.split(" +")[1];
                    if (count >= 3) {
                        break;
                    }
                } else {
                    continue;
                }
            }

            freeSize = (Long.valueOf(free) + Long.valueOf(buff) + Long.valueOf(cache));

            return freeSize;
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "get Free Memory err", e);
            }
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return freeSize;
    }

    /**
     * 返回总的内存.
     *
     * @return 返回整数，单位 MB
     */
    public static int getMemoryTotal() {
        long totalSize = getMemoryTotalKb();
        if (totalSize == -1L) {
            return -1;
        }

        return (int) (totalSize / 1024F);
    }

    /**
     * 返回可用的内存.
     *
     * @return 返回整数，单位 MB
     */
    public static int getMemoryFree() {
        long freeSize = getMemoryFreeKb();
        if (freeSize == -1L) {
            return -1;
        }

        return (int) (freeSize / 1024F);
    }

    /** 获取data文件系统的大小信息 */
    public static long getDataPartitionTotalSize() {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            // long availableBlocks = stat.getAvailableBlocks();
            return blockSize * totalBlocks;
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "", e);
            }
        }

        return 0;
    }

    /** 获取data文件系统剩余空间的大小信息 */
    public static long getDataPartitionFreeSize() {
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            // long totalBlocks = stat.getBlockCount();
            long availableBlocks = stat.getAvailableBlocks();
            return blockSize * availableBlocks;
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "", e);
            }
        }

        return 0;
    }

    /********************* ART ************************/

    public static final String LIBART_SO = "libart.so";

    public static final String LIBDVM_SO = "libdvm.so";

    /** 当前是否是 libart 虚拟机。仅对 Android 4.4 以上有效，在其它平台上返回 false. */
    public static boolean isRunningART() {
        return (Build.VERSION.SDK_INT >= 19) && LIBART_SO.equals(getVMLib());
    }

    /**
     * 对于 Android 4.4 以上系统，利用反射获取系统属性 persist.sys.dalvik.vm.lib
     * 的值，用以区分当前系统的运行环境是 Dalvik 还是 ART
     *
     * @return libart.so/libdvm.so，如果未获取到则返回null
     */
    private static String getVMLib() {
        String vmLib = null;
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                Class<?> clsSystemProperties = Class.forName("android.os.SystemProperties");
                if (clsSystemProperties != null) {
                    Method methodGet = clsSystemProperties.getDeclaredMethod("get", new Class[] {
                        String.class
                    });
                    if (methodGet != null) {
                        methodGet.setAccessible(true);
                        vmLib = (String) methodGet.invoke(null, new Object[] {
                            "persist.sys.dalvik.vm.lib"
                        });
                    }
                }
            } catch (Exception e) {
                vmLib = null;
            }
        }

        return vmLib;
    }

    /**
     * 获取系统默认卡imsi,与建法确认和取得卡0的imsi方法是一样的。
     * @param c
     * @return 默认卡的imsi
     */
    public static String getDefaultImsi(Context c) {
        String imsi = null;
        TelephonyManager telephonyMgr = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyMgr != null) {
            imsi = telephonyMgr.getSubscriberId();
        }
        return imsi;
    }

    /**
     * 收集Kernel（Linux版本、简单介绍）的信息
     * 用一行显示出来
     *
     * @author Jiongxuan Zhang
     */
    public static String getKernelInfo() {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("/proc/version");
        } catch (FileNotFoundException e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            return null;
        }

        String kernelInfo = "";
        try {
            StringBuilder sb = new StringBuilder();
            byte buffer[] = new byte[4096];
            int rc = 0;
            while ((rc = inputStream.read(buffer)) >= 0) {
                if (rc > 0) {
                    sb.append(new String(buffer, 0, rc, "UTF-8"));
                }
            }
            kernelInfo = sb.toString();
        } catch (Exception e) {
            if (DEBUG) {
                Log.e(TAG, "", e);
            }
            return null;
        } finally {
        	if (inputStream != null) {
                try {
                	inputStream.close();
                } catch (Throwable e) {
                    //
                }
            }
        }

        // 替换换行符（变成竖线）
        kernelInfo = kernelInfo.trim().replace("\r", " ").replace("\n", "|");

        if (DEBUG) {
            Log.i(TAG, "Kernel Info:" + kernelInfo);
        }

        return kernelInfo;
    }
}
