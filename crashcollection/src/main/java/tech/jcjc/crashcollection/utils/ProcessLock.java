package tech.jcjc.crashcollection.utils;

import android.content.Context;
import android.util.Log;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import tech.jcjc.crashcollection.AppEnv;

/**
 * 基于RandomAccessFile + FileLock实现的全局进程锁，只能实现进程互斥，不能实现线程互斥
 * 该类会在app files目录下生成构造函数指定的lock文件
 */
public class ProcessLock {
    private static final String TAG = ProcessLock.class.getSimpleName();

    private static final boolean DEBUG = AppEnv.ISAPPDEBUG;

    /**
     * @param appContext Context
     * @param lockName 用于做锁的文件，切忌使用任何真实存在的数据文件
     * @param autoCleanLockfile 是否在锁解除之后，自动删掉用于做锁的文件
     *
     */
    public ProcessLock(Context appContext, String lockName, boolean autoCleanLockfile) {
        mContext = appContext;
        mbAutoClean = autoCleanLockfile;
        mLockFileName = lockName + ".lock";
    }


    /**
     * 尝试获取文件锁
     * @param retryTime 重试次数
     * @param retryPeriod 两次重试之间的间隔时间
     * @param block 是否阻塞获取，如果该项是true，则自动忽略前两个参数，直接堵死到拿到锁或者出现异常
     * @return 是否成功获取锁
     */
    public synchronized boolean tryLock(int retryTime, int retryPeriod, boolean block) {
        if (mbLocked) {
            return mbLocked;
        }
        mBaseFile = mContext.getFileStreamPath(mLockFileName);
        try {
            mLockFile = new RandomAccessFile(mBaseFile, "rw");
        } catch (FileNotFoundException e) {
            //if (DEBUG) {
                Log.w(TAG, "Lock base file failed: " + e.getMessage());
                Log.w(TAG, "Base file: " + mBaseFile);
            //}
            return false;
        }

        mFileChannel = mLockFile.getChannel();
        if (block) {
            try {
                mFileLock = mFileChannel.lock();
                mbLocked = true;
            } catch (Exception e) {
                //lock实际能返回多种类型的Exception，不完全是IOException
                //if (DEBUG) {
                    Log.w(TAG, "Block lock failed: " + e.getMessage());
                //}
            }
        } else {
            while (retryTime > 0) {
                try {
                    mFileLock = mFileChannel.tryLock();
                } catch (IOException e) {
                    if (DEBUG) {
                        Log.w(TAG, "Try lock failed: " + e.getMessage());
                    }
                }
                if (mFileLock != null) {
                    mbLocked = true;
                    break;
                } else {
                    //sleep retry period
                    try {
                        if (retryPeriod > 0) {
                            Thread.sleep(retryPeriod);
                        }
                    } catch (InterruptedException e) {
                        //ignore
                    }
                    retryTime--;
                }
            }
        }

        if (!mbLocked) {
            //没锁成功
            closeFiles();
            if (mBaseFile != null && mbAutoClean) {
                mBaseFile.delete();
                mBaseFile = null;
            }
        }
        return mbLocked;
    }

    /**
     * 释放当前进程锁，以及进程锁所依赖的所有文件关联类，如果autoClean为true，会删除数据文件。
     */
    public synchronized void freeLock() {
        if (DEBUG) {
            Log.d(TAG, "freelock :" + mLockFileName);
        }
        if (mbLocked) {
            if (mbAutoClean) {
                if (DEBUG) {
                    Log.d(TAG, "delete lockfile :" + mBaseFile.getAbsolutePath());
                }
                mBaseFile.delete();
            }
            try {
                mFileLock.release();
            } catch (IOException e) {
                if (DEBUG) {
                    Log.w(TAG, "Release lock failed: " + e.getMessage());
                //damn shit!!!
                }
            }
            mFileLock = null;
            closeFiles();
            mbLocked = false;
        }
    }

    /**
     * 清理文件资源的私有接口
     */
    private void closeFiles() {
        if (mFileChannel != null) {
            try {
                mFileChannel.close();
            } catch (IOException e) {
                if (DEBUG) {
                    Log.w(TAG, "Close channel failed: " + e.getMessage());
                }
            }
            mFileChannel = null;
        }

        if (mLockFile != null) {
            try {
                mLockFile.close();
            } catch (IOException e) {
                if (DEBUG) {
                    Log.w(TAG, "Close file failed: " + e.getMessage());
                }
            }
            mLockFile = null;
        }

        mBaseFile = null;
    }

    /**
     * 预留接口，用于读取lock file内的内容，可以用来扩展做一些拿到锁之后的条件判断
     * 目前扩展此接口供
     * @return
     */
    public synchronized byte getInternalLockByte() {
        if (mbLocked) {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            try {
                mFileChannel.position(0);
                mFileChannel.read(buffer);
            } catch (IOException e) {
                if (DEBUG) {
                    Log.w(TAG, "Read file failed: " + e.getMessage());
                }
            }
            return buffer.get(0);
        }
        return 0;
    }

    /**
     * 预留接口，用于设置lock file内的首个字节，与getInternalLockByte配对使用
     * @param b 设置的byte
     */
    public synchronized void setInternalLockByte(byte b) {
        if (mbLocked) {
            ByteBuffer buffer = ByteBuffer.wrap(new byte[]{b});
            try {
                mFileChannel.position(0);
                mFileChannel.write(buffer);
            } catch (IOException e) {
                if (DEBUG) {
                    Log.w(TAG, "Write file failed: " + e.getMessage());
                }
            }
        }
    }




    @Override
    public String toString() {
        return mLockFileName + " " + mbAutoClean;
    }




    private final Context mContext;
    private final boolean mbAutoClean;
    private final String mLockFileName;

    private File mBaseFile;
    private RandomAccessFile mLockFile;
    private FileLock mFileLock;
    private FileChannel mFileChannel;
    private boolean mbLocked = false;
}
