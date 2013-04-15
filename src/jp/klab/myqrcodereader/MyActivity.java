/*
 * Copyright (C) 2013 KLab Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.klab.myqrcodereader;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.zxing.PlanarYUVLuminanceSource;

public class MyActivity extends Activity implements SurfaceHolder.Callback,
        Handler.Callback, Camera.PreviewCallback {

    private static final String TAG = "QR";
    private MyDecodeThread mDecodeThread = null;
    private MyCameraConfigurationManager mConfigManager;
    private MyFinderView mFinderView;
    private SurfaceView mSurfaceView;
    private Handler mHandler = null;
    private Camera mCamera = null;
    private Boolean mHasSurface;
    private Timer mTimerFocus;
    private boolean mMultiMode = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // フルスクリーンかつタイトル表示無し
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mHasSurface = false;
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mConfigManager = new MyCameraConfigurationManager(this);
        mFinderView = (MyFinderView) findViewById(R.id.finderView);
        mSurfaceView = (SurfaceView) findViewById(R.id.preview_view);
        if (mHandler == null) {
            mHandler = new Handler(this);
            // コード認識用スレッドを開始
            mDecodeThread = new MyDecodeThread(this, new MyResultPointCallback(mFinderView));
            mDecodeThread.start();
        }
        SurfaceHolder holder = mSurfaceView.getHolder();
        if (mHasSurface) {
            // surfaceCreated() ずみで surfaceDestroyed() が未了の状況
            try {
                openCamera(holder);
            } catch (IOException e) {
                Message.obtain(mHandler, R.id.error).sendToTarget();
            }
        } else {
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIt();
    }

    @Override
    // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        if (!mHasSurface) {
            mHasSurface = true;
            try {
                openCamera(holder);
            } catch (IOException e) {
                Message.obtain(mHandler, R.id.error).sendToTarget();
            }
        }
    }

    @Override
    // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;
    }

    @Override
    // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    // Handler.Callback
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
        case R.id.error:
            showDialogMessage("エラーが発生しました", true);
            break;
        case R.id.decode_succeeded: // 認識 OK
            String text = (String) msg.obj;
            _Log.d(TAG, "decoded [" + text + "]");
            if (!mMultiMode) {
                stopIt();
                showDialogMessage(text, true);
            } else {
                mCamera.setOneShotPreviewCallback(this);
            }
            break;
        case R.id.decode_failed: // 認識 NG
            if (mCamera != null) {
                // PreviewCallback を発動させ フレームイメージ取得～認識 を繰り返す
                mCamera.setOneShotPreviewCallback(this);
            }
            break;
        }
        return false;
    }

    @Override
    // Camera.PreviewCallback
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mDecodeThread != null) {
            // プレビューイメージを認識スレッドへ渡しコードの読取りを指示
            Handler h = mDecodeThread.getHandler();
            Message msg = h.obtainMessage(R.id.decode,
                mConfigManager.getCameraResolution().x,
                mConfigManager.getCameraResolution().y, data);
            msg.sendToTarget();
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int w, int h) {
        // プレビューフレームデータからファインダ矩形範囲の PlanarYUVLuminanceSource を生成
        int left = mFinderView.getLeft();
        int top = mFinderView.getTop();
        int width = mFinderView.getWidth();
        int height = mFinderView.getHeight();
        return new PlanarYUVLuminanceSource(data, w, h, left, top, width, height, false);
    }    
    
    private void stopIt() {
        closeCamera();
        mHandler = null;
        if (mDecodeThread != null) {
            // 認識スレッドを終了させる
            Message msg = Message.obtain(mDecodeThread.getHandler(), R.id.quit);
            msg.sendToTarget();
            try {
                mDecodeThread.join(500L);
            } catch (InterruptedException e) {
            }
            mDecodeThread = null;
        }
        if (!mHasSurface) {
            SurfaceHolder holder = mSurfaceView.getHolder();
            holder.removeCallback(this);
        }        
    }

    private void openCamera(SurfaceHolder holder) throws IOException {
        if (mCamera == null) {
            mCamera = Camera.open();
            if (mCamera == null) {
                throw new IOException();
            }
        }
        mCamera.setPreviewDisplay(holder);
        mConfigManager.initFromCameraParameters(mCamera);
        mConfigManager.setDesiredCameraParameters(mCamera, false);
        mCamera.startPreview();
        if (mTimerFocus == null) {
            mTimerFocus = new Timer(false);
            mTimerFocus.schedule(new TimerTask() {
                @Override
                public void run() {
                    mCamera.autoFocus(null);
                }
            }, 500, 2000); // 2秒間隔でオートフォーカス
        }
        // PreviewCallback を発動させ初回の認識処理を駆動
        mCamera.setOneShotPreviewCallback(this);
    }

    private void closeCamera() {
        if (mTimerFocus != null) {
            mTimerFocus.cancel();
            mTimerFocus = null;
        }
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private void showDialogMessage(String msg, final boolean bFinish) {
        new AlertDialog.Builder(this).setTitle(R.string.app_name)
                .setIcon(R.drawable.ic_launcher).setMessage(msg)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (bFinish) {
                            finish();
                        }
                    }
                }).show();
    }

}
