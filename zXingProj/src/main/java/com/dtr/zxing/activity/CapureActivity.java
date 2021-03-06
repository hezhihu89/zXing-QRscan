/*
 * Copyright (C) 2008 ZXing authors
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
package com.dtr.zxing.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.dtr.zxing.R;
import com.dtr.zxing.camera.CameraManager;
import com.dtr.zxing.decode.DecodeThread;
import com.dtr.zxing.utils.BeepManager;
import com.dtr.zxing.utils.CaptureActivityHandler;
import com.dtr.zxing.utils.InactivityTimer;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CapureActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = CaptureActivity.class.getSimpleName();

    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;

    private SurfaceView scanPreview = null;
    private RelativeLayout scanContainer;
    private RelativeLayout scanCropView;
    private ImageView scanLine;

    private Rect mCropRect = null;
    private Dialog mDialog;
    private Window window;
    private WindowManager.LayoutParams attributes;

    private Button mScan, mSave;
    private Button mCreate;
    private View dialogView;
    private ImageView mShowQR;
    private EditText mEdit;
    private CheckBox mAddBt;
    private Bitmap mQRBitmap = null;
    private Bitmap tpmBitmap = null;

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    private boolean isHasSurface = false;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        initView();
        initEvent();


    }


    private void initEvent() {
        mScan.setOnClickListener(this);
        mCreate.setOnClickListener(this);
        mSave.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.scan:
                initDialog();
                onRealCamera();
                break;
            case R.id.createQR:
                String str = mEdit.getText().toString().trim();
                Bitmap bitmap = null;
                if (mAddBt.isChecked()) {
                    bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.win);
                }
                //显示到一个ImageView上面
                mShowQR.setImageBitmap(createQRImage(str, bitmap));
                break;
            case R.id.sava:
                saveQR();
                break;

        }
    }

    private void saveQR() {
        if (mQRBitmap != null && !mQRBitmap.equals(tpmBitmap)) {
            File file = new File("/mnt/sdcard/TIFA/" + System.currentTimeMillis() + ".jpg");
            Log.d("TAG", file.getAbsolutePath().toString());
            FileOutputStream saveIO = null;
            try {
                File files = new File("/mnt/sdcard/TIFA");
                if (!files.exists() || !files.isDirectory()) {
                    files.mkdir();
                }
                file.createNewFile();
                saveIO = new FileOutputStream(file);
                mQRBitmap.compress(Bitmap.CompressFormat.JPEG, 1, saveIO);
                saveIO.flush();
                Toast.makeText(this, "已保存", Toast.LENGTH_LONG).show();
                tpmBitmap = mQRBitmap;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (saveIO != null) {
                    try {
                        saveIO.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            Toast.makeText(this, "重复保存对象", Toast.LENGTH_LONG).show();
        }

    }

    private void initView() {
        setContentView(R.layout.main_layout);
        mScan = (Button) findViewById(R.id.scan);
        mCreate = (Button) findViewById(R.id.createQR);
        mShowQR = (ImageView) findViewById(R.id.show_qr);
        mEdit = (EditText) findViewById(R.id.content);
        mAddBt = (CheckBox) findViewById(R.id.set_bit);
        mSave = (Button) findViewById(R.id.sava);

    }

    private void initDialog() {
        //初始化window界面
        mDialog = new Dialog(this);
        window = mDialog.getWindow();
        dialogView = View.inflate(this, R.layout.activity_capture, null);
        window.setContentView(dialogView);
        attributes = window.getAttributes();
        attributes.width = attributes.MATCH_PARENT;
        attributes.height = attributes.MATCH_PARENT;
        window.setWindowAnimations(android.R.style.Animation_InputMethod);
        window.setAttributes(attributes);


        scanPreview = (SurfaceView) dialogView.findViewById(R.id.capture_preview);
        scanContainer = (RelativeLayout) dialogView.findViewById(R.id.capture_container);
        scanCropView = (RelativeLayout) dialogView.findViewById(R.id.capture_crop_view);
        scanLine = (ImageView) dialogView.findViewById(R.id.capture_scan_line);

        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);

        TranslateAnimation animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT,
                0.9f);
        animation.setDuration(4500);
        animation.setRepeatCount(-1);
        animation.setRepeatMode(Animation.RESTART);
        scanLine.startAnimation(animation);
        mDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraManager == null) {
            cameraManager = new CameraManager(getApplication());
            handler = null;
        }


    }

    private void onRealCamera() {
        Log.d("TAG", "重新获取焦点");
        // CameraManager must be initialized here, not in onCreate(). This is
        // necessary because we don't
        // want to open the camera driver and measure the screen size if we're
        // going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the
        // wrong size and partially
        // off screen.


        if (isHasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(scanPreview.getHolder());
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            scanPreview.getHolder().addCallback(this);
        }

        inactivityTimer.onResume();
    }


    @Override
    protected void onPause() {
        Log.d("TAG", "再次推出");

        //inactivityTimer.onPause();
        //  beepManager.close();
        //  cameraManager.closeDriver();
     /*   if (!isHasSurface) {
            scanPreview.getHolder().removeCallback(this);
        }*/
        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (inactivityTimer != null) {
            inactivityTimer.shutdown();
        }

        cameraManager.closeDriver();
        cameraManager = null;
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "++++++++++++++++++++++进入++++++++++++++++");
        onResume();
        if (!isHasSurface) {
            isHasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e(TAG, "++++++++++++++++++++++退出+++++++++++++++++");

        if (!isHasSurface) {
            scanPreview.getHolder().removeCallback(this);
        }
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }

        inactivityTimer.onPause();
        beepManager.close();

        isHasSurface = false;

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     *
     * @param rawResult The contents of the barcode.
     * @param bundle    The extras
     */
    public void handleDecode(Result rawResult, Bundle bundle) {
        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate();

        bundle.putInt("width", mCropRect.width());
        bundle.putInt("height", mCropRect.height());
        bundle.putString("result", rawResult.getText());

        startActivity(new Intent(CapureActivity.this, ResultActivity.class).putExtras(bundle));
        finish();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager, DecodeThread.ALL_MODE);
            }

            initCrop();
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        // camera error
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage("相机打开出错，请稍后重试");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }

        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        builder.show();
    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
    }

    public Rect getCropRect() {
        return mCropRect;
    }

    /**
     * 初始化截取的矩形区域
     */
    private void initCrop() {
        int cameraWidth = cameraManager.getCameraResolution().y;
        int cameraHeight = cameraManager.getCameraResolution().x;

        /** 获取布局中扫描框的位置信息 */
        int[] location = new int[2];
        scanCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1] - getStatusBarHeight();

        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        /** 获取布局容器的宽高 */
        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        /** 计算最终截取的矩形的左上角顶点x坐标 */
        int x = cropLeft * cameraWidth / containerWidth;
        /** 计算最终截取的矩形的左上角顶点y坐标 */
        int y = cropTop * cameraHeight / containerHeight;

        /** 计算最终截取的矩形的宽度 */
        int width = cropWidth * cameraWidth / containerWidth;
        /** 计算最终截取的矩形的高度 */
        int height = cropHeight * cameraHeight / containerHeight;

        /** 生成最终的截取的矩形 */
        mCropRect = new Rect(x, y, width + x, height + y);
    }

    private int getStatusBarHeight() {
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = Integer.parseInt(field.get(obj).toString());
            return getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }


    public Bitmap createQRImage(String url, Bitmap logoBm) {
        try {
            //判断URL合法性
            if (url == null || "".equals(url) || url.length() < 1) {
                return null;
            }

            int QR_WIDTH = 600;
            int QR_HEIGHT = 600;
            Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>();
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            //图像数据转换，使用了矩阵转换
            BitMatrix bitMatrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT, hints);
            int[] pixels = new int[QR_WIDTH * QR_HEIGHT];
            //下面这里按照二维码的算法，逐个生成二维码的图片，
            //两个for循环是图片横列扫描的结果
            for (int y = 0; y < QR_HEIGHT; y++) {
                for (int x = 0; x < QR_WIDTH; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * QR_WIDTH + x] = 0xff000000;
                    } else {
                        pixels[y * QR_WIDTH + x] = 0xffffffff;
                    }
                }
            }


            //生成二维码图片的格式，使用ARGB_8888
            mQRBitmap = Bitmap.createBitmap(QR_WIDTH, QR_HEIGHT, Bitmap.Config.ARGB_8888);
            mQRBitmap.setPixels(pixels, 0, QR_WIDTH, 0, 0, QR_WIDTH, QR_HEIGHT);


            if (logoBm != null) {
                mQRBitmap = addLogo(mQRBitmap, logoBm);
            }


        } catch (WriterException e) {
            e.printStackTrace();
        }

        return mQRBitmap;
    }


    /**
     * 在二维码中间添加Logo图案
     */
    private static Bitmap addLogo(Bitmap src, Bitmap logo) {
        if (src == null) {
            return null;
        }

        if (logo == null) {
            return src;
        }

        //获取图片的宽高
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        int logoWidth = logo.getWidth();
        int logoHeight = logo.getHeight();

        if (srcWidth == 0 || srcHeight == 0) {
            return null;
        }

        if (logoWidth == 0 || logoHeight == 0) {
            return src;
        }

        //logo大小为二维码整体大小的1/5
        float scaleFactor = srcWidth * 1.0f / 5 / logoWidth;
        Bitmap bitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(src, 0, 0, null);
            canvas.scale(scaleFactor, scaleFactor, srcWidth / 2, srcHeight / 2);
            canvas.drawBitmap(logo, (srcWidth - logoWidth) / 2, (srcHeight - logoHeight) / 2, null);

            canvas.save(Canvas.ALL_SAVE_FLAG);
            canvas.restore();
        } catch (Exception e) {
            bitmap = null;
            e.getStackTrace();
        }

        return bitmap;
    }
}