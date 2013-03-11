package com.example.marketday2013;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import com.example.market_day_2013.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;

public class BlobView extends Activity implements CvCameraViewListener {
	
	// USB Stuff
	
	private static final byte CMD_HORIZONTAL = 10;
	private static final byte CMD_VERTICAL = 12;
	
	private static final byte TOGGLE_LED_COMMAND = 15;

	private UsbManager mUsbManager;

	private UsbAccessory mAccessory;

	private ParcelFileDescriptor mFileDescriptor;

	private FileOutputStream mOutputStream;

	private ImageView mStatusLed;

	private BroadcastReceiver mReceiver;
	
	// Face Detection Stuff

    private static final String    TAG                 = "OCVSample::Activity";
    private static final Scalar    FACE_RECT_COLOR     = new Scalar(0, 255, 0, 255);
    private static final Scalar	   CENTER_COLOR        = new Scalar(255, 0, 0, 255);
    public static final int        JAVA_DETECTOR       = 0;
    public static final int        NATIVE_DETECTOR     = 1;

    private MenuItem               mItemFace50;
    private MenuItem               mItemFace40;
    private MenuItem               mItemFace30;
    private MenuItem               mItemFace20;
    private MenuItem               mItemType;

    private Mat                    mRgba;
    private Mat                    mGray;
    private File                   mCascadeFile;
    private CascadeClassifier      mJavaDetector;
    private DetectionBasedTracker  mNativeDetector;

    private int                    mDetectorType       = JAVA_DETECTOR;
    private String[]               mDetectorName;

    private float                  mRelativeFaceSize   = 0.2f;
    private int                    mAbsoluteFaceSize   = 0;

    private CameraBridgeViewBase   mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public BlobView() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";
        mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        
        // USB Shield
//        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mUsbManager = UsbManager.getInstance(this);
		setContentView(R.layout.main);
		setupStatusLed();
		
		// OpenCV
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        super.onPause();
        
        // USB Shield
        closeAccessory();
		unregisterReceiver(mReceiver);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        
        // USB Shield
        setupDetachingAccessoryHandler();
		reOpenAccessoryIfNecessary();
		
		// OpenCV
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(Mat inputFrame) {

        inputFrame.copyTo(mRgba);
        Imgproc.cvtColor(inputFrame, mGray, Imgproc.COLOR_RGBA2GRAY);

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }

        MatOfRect faces = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }
        else if (mDetectorType == NATIVE_DETECTOR) {
            if (mNativeDetector != null)
                mNativeDetector.detect(mGray, faces);
        }
        else {
            Log.e(TAG, "Detection method is not selected!");
        }
        
        Rect[] facesArray = faces.toArray();
        double cx=0, cy=0;
        for (int i = 0; i < facesArray.length; i++) {
        	Point tl = facesArray[i].tl();
        	Point br = facesArray[i].br();
        	cx += (tl.x + br.x)/2;
        	cy += (tl.y + br.y)/2;
            Core.rectangle(mRgba, tl, br, FACE_RECT_COLOR, 3);
        }
        cx /= facesArray.length;
        cy /= facesArray.length;
        
        byte hx = (byte)128, vx = (byte)128;
        if (facesArray.length != 0) {
	        hx = (byte) (255*cx/mRgba.width());
	        vx = (byte) (255*cy/mRgba.height());
        }
        sendCommand(CMD_HORIZONTAL, hx);
        
        Point center = new Point(cx, cy);
        
        Core.circle(mRgba, center, 5, CENTER_COLOR);

        return mRgba;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        mItemType   = menu.add(mDetectorName[mDetectorType]);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);
        else if (item == mItemType) {
            mDetectorType = (mDetectorType + 1) % mDetectorName.length;
            item.setTitle(mDetectorName[mDetectorType]);
            setDetectorType(mDetectorType);
        }
        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void setDetectorType(int type) {
        if (mDetectorType != type) {
            mDetectorType = type;

            if (type == NATIVE_DETECTOR) {
                Log.i(TAG, "Detection Based Tracker enabled");
                mNativeDetector.start();
            } else {
                Log.i(TAG, "Cascade detector enabled");
                mNativeDetector.stop();
            }
        }
    }
    
    private void setupStatusLed() {
		mStatusLed = (ImageView) findViewById(R.id.status_led);
	}
    
	private void setupDetachingAccessoryHandler() {
		mReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
					closeAccessory();
					finish();
				}
			}
		};
		IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mReceiver, filter);
	}

	private void reOpenAccessoryIfNecessary() {
		setStatusWaiting();
		if (mOutputStream != null) {
			setStatusConnected();
			return;
		}
		Intent intent = getIntent();
		String action = intent.getAction();
		if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
//				mAccessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
			UsbAccessory accessory = UsbManager.getAccessory(intent);
			openAccessory();
		}
	}

	private void openAccessory() {
		try {
			mFileDescriptor = mUsbManager.openAccessory(mAccessory);
			if (mFileDescriptor != null) {
				FileDescriptor fd = mFileDescriptor.getFileDescriptor();
				mOutputStream = new FileOutputStream(fd);
				setStatusConnected();
			}
		} catch (IllegalArgumentException ex) {
			// Accessory detached while activity was inactive
			closeAccessory();
		}
	}

	private void closeAccessory() {
		try {
			if (mOutputStream != null) {
				mOutputStream.close();
			}
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mOutputStream = null;
			mFileDescriptor = null;
			mAccessory = null;
		}
		setStatusDisconnected();
	}

	private void sendCommand(byte command, byte arg) {
		if (mOutputStream != null) {
			try {
				byte[] msg = {command, arg};
				mOutputStream.write(msg);
			} catch (IOException e) {
				// Do nothing
			}
		} else {
			closeAccessory();
		}
	}

	private void setStatusConnected() {
		mStatusLed.setImageResource(R.drawable.green_led);
	}

	private void setStatusWaiting() {
		mStatusLed.setImageResource(R.drawable.yellow_led);
	}

	private void setStatusDisconnected() {
		mStatusLed.setImageResource(R.drawable.red_led);
	}	
}

