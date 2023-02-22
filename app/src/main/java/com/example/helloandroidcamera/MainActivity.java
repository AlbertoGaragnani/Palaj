package com.example.helloandroidcamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ImageAnalysis.Analyzer{
    private ListenableFuture<ProcessCameraProvider> provider;

    private static final int PERMISSION_REQUEST_CODE = 200;

    private Button picture_bt, analysis_bt;
    private PreviewView pview;
    private ImageView imview;
    private ImageCapture imageCapt;
    private ImageAnalysis imageAn;
    private boolean analysis_on;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (! checkPermission())
            requestPermission();

        picture_bt = findViewById(R.id.picture_bt);
        analysis_bt = findViewById(R.id.analysis_bt);
        pview = findViewById(R.id.previewView);
        imview = findViewById(R.id.imageView);

        picture_bt.setOnClickListener(this);
        analysis_bt.setOnClickListener(this);
        this.analysis_on = false;

        provider = ProcessCameraProvider.getInstance(this);
        provider.addListener( () ->
        {
            try{
                ProcessCameraProvider cameraProvider = provider.get();
                startCamera(cameraProvider);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            return false;
        }
        return true;
    }

    private void requestPermission() {

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Permission Granted", Toast.LENGTH_SHORT).show();

                    // main logic
                } else {
                    Toast.makeText(getApplicationContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {
                            showMessageOKCancel("You need to allow access permissions",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermission();
                                            }
                                        }
                                    });
                        }
                    }
                }
                break;
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void startCamera(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        CameraSelector camSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(pview.getSurfaceProvider());

        imageCapt = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        imageAn = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAn.setAnalyzer(getExecutor(), this);

        cameraProvider.bindToLifecycle((LifecycleOwner)this, camSelector, preview, imageCapt, imageAn);
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.picture_bt:
                capturePhoto();
                break;

            case R.id.analysis_bt:
                this.analysis_on = !this.analysis_on;
                break;
        }
    }

    public void capturePhoto() {
        //Es. SISDIG_2021127_189230.jpg
        String pictureName = "SISDIG_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpeg";
        imageCapt.takePicture(
                getExecutor(),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(ImageProxy image) {
                        //Create the picture's metadata
                        ContentValues newPictureDetails = new ContentValues();
                        newPictureDetails.put(MediaStore.Images.Media._ID, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.ORIENTATION, String.valueOf(-image.getImageInfo().getRotationDegrees()));
                        newPictureDetails.put(MediaStore.Images.Media.DISPLAY_NAME, pictureName);
                        newPictureDetails.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        newPictureDetails.put(MediaStore.Images.Media.WIDTH, image.getWidth());
                        newPictureDetails.put(MediaStore.Images.Media.HEIGHT, image.getHeight());
                        newPictureDetails.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/SistemiDigitaliM");
                        OutputStream stream = null;
                        try {
                            //Add picture to MediaStore in order to make it accessible to other apps
                            //The result of the insert is the handle to the picture inside the MediaStore
                            Uri picturePublicUri = getApplicationContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, newPictureDetails);
                            stream = getApplicationContext().getContentResolver().openOutputStream(picturePublicUri);
                            Bitmap bitmapImage = pview.getBitmap();
                            if (!bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, stream)) { //Save the image in the gallery
                                //Error
                            }

                            image.close();
                            stream.close();

                            Toast.makeText(getApplicationContext(), "Picture Taken", Toast.LENGTH_SHORT).show();
                        } catch (Exception exception) {
                            exception.printStackTrace();
                            Toast.makeText(getApplicationContext(), "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        if(this.analysis_on)
        {
            Bitmap conv = pview.getBitmap();
            // Do something here!!!
            conv = toGrayscale(conv);
            this.imview.setImageBitmap( conv );
        }
        image.close();

    }

    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

}