package com.ImageCaptioning;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.ImageCaptioning.captioner.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {
    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_IMAGE_SELECT = 200;
    public static final int MEDIA_TYPE_IMAGE = 1;

    //camera variables start here
    private Camera mCamera;
    private CameraPreview mPreview;
    private Camera.PictureCallback mPicture;
    private FrameLayout preview;
    private File pictureFile;
    private Button captureButton;
    //Camera variables end here
    private Button btnCamera;
    private Button btnSelect;
    private Button btnRepeat;
    private Button btnSpeak;
    //private ImageView ivCaptured;
    private TextView tvLabel;
    private Uri fileUri;
    private ProgressDialog dialog;
    private Bitmap bmp;
    private Captioner captioner;
    private TextToSpeech tts;
    File sdcard = Environment.getExternalStorageDirectory();
    //different configs
    //String modelDir = "/Caffe_Mobile";
    //String cnn_modelProto = modelDir + "/deploy.prototxt";
    String modelDir = "/sdcard/Captioner";
    String cnn_modelProto = modelDir + "/cnn_deploy.prototxt";
    String lstm_modelProto = modelDir + "/lstm_deploy.prototxt";
    String modelBinary = modelDir + "/cnn_lstm.caffemodel";
    String vocabulary = modelDir + "/vocabulary.txt";

    static {
        System.loadLibrary("caffe");
        System.loadLibrary("captioner_jni");
    }

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission1 = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission1 != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        verifyStoragePermissions(this);
        setContentView(R.layout.activity_main);

        // Create an instance of Camera
        mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        tts = new TextToSpeech(this.getApplicationContext(), new TextToSpeech.OnInitListener()
        {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.US);
                    tts.setPitch(1.3f);
                    tts.setSpeechRate(1f);
                }
            }
        });

        mPicture = new Camera.PictureCallback() {

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
                if (pictureFile == null){
                    Log.d(TAG, "Error creating media file, check storage permissions: ");// + e.getMessage());
                    return;
                }

                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                    //added by gftm
                    fileUri = Uri.fromFile(pictureFile);
                    showDialogAndPredict(fileUri.getPath());
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }
        };
        // Add a listener to the Capture button
        captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // get an image from the camera
                        mCamera.takePicture(null, null, mPicture);
                    }
                }
        );

        //tts.setLanguage(Locale.US);
        //tts.speak("a boy is playing football" +
        //       "aa", TextToSpeech.QUEUE_FLUSH, null);

        //ivCaptured = (ImageView) findViewById(R.id.ivCaptured);
        tvLabel = (TextView) this.findViewById(R.id.tvlabel);

        /*btnCamera = (Button) this.findViewById(R.id.id_btnCamera);
        btnCamera.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                initPrediction();
                fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);
                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(i, REQUEST_IMAGE_CAPTURE);
            }
        });*/


        btnRepeat = (Button) this.findViewById(R.id.id_btnRepeat);
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                preview.addView(mPreview);
                btnSelect.setVisibility(View.VISIBLE);
                captureButton.setVisibility(View.VISIBLE);
                tvLabel.setText("");
                tvLabel.setVisibility(View.GONE);
                btnSpeak.setVisibility(View.GONE);
                btnRepeat.setVisibility(View.GONE);
            }
        });

        btnSelect = (Button) this.findViewById(R.id.id_btnSelect);
        btnSelect.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                //initPrediction(); removed by gftm
                Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, REQUEST_IMAGE_SELECT);
            }
        });

        btnSpeak = (Button) this.findViewById(R.id.id_btnSpeak);
        btnSpeak.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                speak_out();
            }
        });

        captioner = new Captioner();
        captioner.setNumThreads(32);
        captioner.loadModel(cnn_modelProto,lstm_modelProto, modelBinary,vocabulary);

        float[] meanValues = {104, 117, 123};
        captioner.setMean(meanValues);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if ((requestCode == REQUEST_IMAGE_CAPTURE || requestCode == REQUEST_IMAGE_SELECT) && resultCode == RESULT_OK) {
            String imgPath;

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                imgPath = fileUri.getPath();
            } else {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = MainActivity.this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgPath = cursor.getString(columnIndex);
                cursor.close();
            }

            showDialogAndPredict(imgPath);

        }/* else {
            //btnCamera.setEnabled(true);
            btnSelect.setEnabled(true);
        }*/

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showDialogAndPredict(String imgPath){
        bmp = BitmapFactory.decodeFile(imgPath);
        Log.d(LOG_TAG, imgPath);
        Log.d(LOG_TAG, String.valueOf(bmp.getHeight()));
        Log.d(LOG_TAG, String.valueOf(bmp.getWidth()));

        preview.removeView(mPreview);
        preview.setBackground(new BitmapDrawable(rotateBitmap(bmp,90.0f)));
        captureButton.setVisibility(View.GONE);
        btnSelect.setVisibility(View.GONE);

        dialog = ProgressDialog.show(MainActivity.this, "I am captioning...", "Please wait.", true);

        CNNTask cnnTask = new CNNTask( );
        cnnTask.execute(imgPath);
    }

    private void initPrediction() {
        tvLabel.setText("");
        tvLabel.setVisibility(View.VISIBLE);
        btnSpeak.setVisibility(View.VISIBLE);
        btnRepeat.setVisibility(View.VISIBLE);
    }

    private void speak_out() {
        String caption=tvLabel.getText().toString();
        tts.speak(caption, TextToSpeech.QUEUE_ADD, null,null);
    }

    private class CNNTask extends AsyncTask<String, Void, String> {
        //private CNNListener listener;
        private long startTime;

        //public CNNTask(CNNListener listener) {
        //    this.listener = listener;
        //}

        @Override
        protected String doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            return captioner.predictImage(strings[0]);
        }

        @Override
        protected void onPostExecute(String string) {
            Log.i(LOG_TAG, String.format("elapsed wall time: %d ms", SystemClock.uptimeMillis() - startTime));
            onTaskCompleted(string);
            super.onPostExecute(string);
        }
    }

    /**
     * display the results on screen
     */
    public void onTaskCompleted(String result) {
        initPrediction();
        tvLabel.setText(result);
        tvLabel.setTextSize(18);
        tts.speak(result, TextToSpeech.QUEUE_ADD, null,null);
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /**
     * Create a file Uri for saving an image or video
     */
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File("/sdcard/", "Captioner");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }
}