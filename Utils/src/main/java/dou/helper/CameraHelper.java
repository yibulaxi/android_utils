package dou.helper;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Iterator;
import java.util.List;

import dou.utils.DLog;
import dou.utils.DisplayUtil;

import static android.hardware.Camera.*;

@SuppressWarnings("deprecation")
public class CameraHelper implements PreviewCallback {

    private Camera camera = null;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder = null;

    private PreviewFrameListener previewFrameListener;
    private Context context;
    private Camera.Size previewSize;

    private int cameraFacing = CameraInfo.CAMERA_FACING_FRONT;
    private int sw, sh;
    private int camera_max_width = 0;

    public CameraHelper(Context context, CameraParams params) {
        this.context = context;
        assert params != null;
        this.surfaceView = params.surfaceView;
        assert this.surfaceView != null;
        this.camera_max_width = params.max_width;
        this.previewFrameListener = params.previewFrameListener;
        this.cameraFacing = params.firstCameraId;
        this.sw = DisplayUtil.getScreenWidthPixels(context);
        this.sh = DisplayUtil.getScreenHeightPixels(context);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                openCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                initCamera();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopCamera();
            }
        });
    }


    private void openCamera() {
        DLog.d("openCamera");

        try {
            if (!hasFacing(cameraFacing)) {
                cameraFacing = (cameraFacing == CameraInfo.CAMERA_FACING_FRONT ?
                        CameraInfo.CAMERA_FACING_BACK : CameraInfo.CAMERA_FACING_FRONT);
            }
            try {
                camera = open(cameraFacing);
            } catch (Exception e) {

            }
            //某些设备摄像头无法开启,使用open(),设置当前摄像头为后置模式
            if (camera == null) {
                camera = open();
                cameraFacing = CameraInfo.CAMERA_FACING_BACK;
            }
            camera.setPreviewDisplay(surfaceHolder);
            initCamera();
        } catch (Exception e) {
            e.printStackTrace();
            if (null != camera) {
                camera.release();
                camera = null;
            }
        }
    }

    private void initCamera() {
        if (null != camera) {
            try {
                Camera.Parameters parameters = camera.getParameters();
                setOptimalPreviewSize(parameters, camera_max_width);

                if (((Activity) context).getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
//                    camera.setDisplayOrientation(cameraFacing == CameraInfo.CAMERA_FACING_FRONT ? 90 : 270);
                    camera.setDisplayOrientation(90);
                } else if (((Activity) context).getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    camera.setDisplayOrientation(0);
                } else if (((Activity) context).getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                    camera.setDisplayOrientation(270);
                } else if (((Activity) context).getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
//                    camera.setDisplayOrientation(cameraFacing == CameraInfo.CAMERA_FACING_FRONT ? 0 : 180);
                    camera.setDisplayOrientation(0);
                }
                camera.setParameters(parameters);
                camera.cancelAutoFocus();
                startPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setOptimalPreviewSize(Camera.Parameters cameraParams,
                                       int targetWidth) {
        List<Camera.Size> supportedPreviewSizes = cameraParams.getSupportedPreviewSizes();

        if (null == supportedPreviewSizes) {
        } else {
            Camera.Size optimalSize = null;
            if (targetWidth == -1) {
                //大于640,且跟屏幕分辨率最接近的分辨率
                double minDiff = 1.7976931348623157E308D;
                Iterator mIterator = supportedPreviewSizes.iterator();
                while (mIterator.hasNext()) {
                    Camera.Size size = (Camera.Size) mIterator.next();
                    if (size.width > 640 &&
                            (double) Math.abs(size.width - 640) < minDiff
                            && (size.width * sh == size.height * sw
                            || size.width * sw == size.height * sh)) {
                        optimalSize = size;
                        minDiff = (double) Math.abs(size.width - 640);
                    }
                }
            }

            if (optimalSize == null) {
                targetWidth = targetWidth == -1 ? 640 : targetWidth;
                double minDiff = 1.7976931348623157E308D;
                Iterator mIterator = supportedPreviewSizes.iterator();
                while (mIterator.hasNext()) {
                    Camera.Size size = (Camera.Size) mIterator.next();
                    if ((double) Math.abs(size.width - targetWidth) < minDiff) {
                        optimalSize = size;
                        minDiff = (double) Math.abs(size.width - targetWidth);
                    }
                }
            }
            setPreviewSize(optimalSize);
            int iw = optimalSize.width;
            int ih = optimalSize.height;

            Log.d("CameraHelper", iw + ":" + ih + ":" + sh + ":" + sw);

            if (iw * sh <= ih * sw) {
                surfaceView.getLayoutParams().width = sh * iw / ih;
                surfaceView.getLayoutParams().height = sh;
            } else {
                surfaceView.getLayoutParams().width = sw;
                surfaceView.getLayoutParams().height = sw * iw / ih;
            }

            surfaceView.requestLayout();
            cameraParams.setPreviewSize(iw, ih);
        }
    }


    public Camera.Size getPreviewSize() {
        return previewSize;
    }

    public void stopCamera() {
        if (null != camera) {
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private void setPreviewSize(Camera.Size previewSize) {
        this.previewSize = previewSize;
    }

    public int switchCameraId() {
        if (!hasFacing(CameraInfo.CAMERA_FACING_FRONT)) return cameraFacing;
        cameraFacing = (cameraFacing == CameraInfo.CAMERA_FACING_FRONT ?
                CameraInfo.CAMERA_FACING_BACK : CameraInfo.CAMERA_FACING_FRONT);
        stopCamera();
        openCamera();
        return cameraFacing;
    }

    public int getCameraId() {
        return cameraFacing;
    }

    public void startPreview() {
        camera.startPreview();
        camera.setPreviewCallbackWithBuffer(this);
        camera.addCallbackBuffer(new byte[((previewSize.width * previewSize.height) * ImageFormat.getBitsPerPixel(ImageFormat.NV21)) / 8]);
    }

    public void stopPreview() {
        if (camera != null)
            camera.stopPreview();
    }

    public interface PreviewFrameListener {
        void onPreviewFrame(byte[] data, Camera camera);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        camera.addCallbackBuffer(data);
        if (previewFrameListener != null)
            previewFrameListener.onPreviewFrame(data, camera);
    }


    private boolean hasFacing(int facing) {
        CameraInfo info = new CameraInfo();
        for (int i = 0; i < getNumberOfCameras(); i++) {
            getCameraInfo(i, info);

            if (info.facing == facing) {
                return true;
            }
        }
        return false;
    }


}
