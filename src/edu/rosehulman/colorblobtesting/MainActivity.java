package edu.rosehulman.colorblobtesting;

import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements CvCameraViewListener2 {

  /** References to the UI widgets used in this demo app. */
  private TextView mLeftRightLocationTextView, mTopBottomLocationTextView, mSizePercentageTextView;

  /** Constants and variables used by OpenCV4Android. Don't mess with these. ;) */
  private ColorBlobDetector mDetector;
  private Scalar CONTOUR_COLOR = new Scalar(0, 0, 255, 255);
  private CameraBridgeViewBase mOpenCvCameraView;
  private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
    @Override
    public void onManagerConnected(int status) {
    switch (status) {
    case LoaderCallbackInterface.SUCCESS:
      mOpenCvCameraView.enableView();
      break;
    default:
      super.onManagerConnected(status);
      break;
    }
    }
  };
  
  /** Target color. An inside cone has an orange hue around 5 - 15, full saturation and value. (change as needed) */
  private static final int TARGET_COLOR_HUE = 10;
  private static final int TARGET_COLOR_SATURATION = 255;
  private static final int TARGET_COLOR_VALUE = 255;

  /** Target color. A cone has an orange hue around 5 - 15, full saturation and value. (change as needed) */
  private static final int TARGET_COLOR_HUE_RANGE = 25;
  private static final int TARGET_COLOR_SATURATION_RANGE = 50;
  private static final int TARGET_COLOR_VALUE_RANGE = 50;
  
  /** Minimum size needed to consider the target a cone. (change as needed) */
  private static final double MIN_SIZE_PERCENTAGE = 0.001;

  /** Screen size variables that will become constants once the camera view size is known. */
  private double mCameraViewWidth;
  private double mCameraViewHeight;
  private double mCameraViewArea;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    mLeftRightLocationTextView = (TextView)findViewById(R.id.left_right_location_value);
    mTopBottomLocationTextView = (TextView)findViewById(R.id.top_bottom_location_value);
    mSizePercentageTextView = (TextView)findViewById(R.id.size_percentage_value);
    
    mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.color_blob_detection_activity_surface_view);
    mOpenCvCameraView.setCvCameraViewListener(this);
  }
  

  @Override
  public void onPause() {
    super.onPause();
    if (mOpenCvCameraView != null) {
      mOpenCvCameraView.disableView();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
  }


  /** Displays the blob target info in the text views. */
  public void onImageRecComplete(boolean coneFound, double leftRightLocation, double topBottomLocation, double sizePercentage) {
    if (coneFound) {
      mLeftRightLocationTextView.setText(String.format("%.3f", leftRightLocation));
      mTopBottomLocationTextView.setText(String.format("%.3f", topBottomLocation));
      mSizePercentageTextView.setText(String.format("%.5f", sizePercentage));
    } else {
      mLeftRightLocationTextView.setText("---");
      mTopBottomLocationTextView.setText("---");
      mSizePercentageTextView.setText("---");
    }
  }


  @Override
  public void onCameraViewStarted(int width, int height) {
    mDetector = new ColorBlobDetector();
    
    // Setup the target color.
    Scalar targetColorHsv = new Scalar(255);
    targetColorHsv.val[0] = TARGET_COLOR_HUE;
    targetColorHsv.val[1] = TARGET_COLOR_SATURATION;
    targetColorHsv.val[2] = TARGET_COLOR_VALUE;
    mDetector.setHsvColor(targetColorHsv);  
    
    // Setup the range of values around the color to accept.
    Scalar colorRangeHsv = new Scalar(255);
    colorRangeHsv.val[0] = TARGET_COLOR_HUE_RANGE;
    colorRangeHsv.val[1] = TARGET_COLOR_SATURATION_RANGE;
    colorRangeHsv.val[2] = TARGET_COLOR_VALUE_RANGE;
    mDetector.setColorRadius(colorRangeHsv);

    // Record the screen size constants
    mCameraViewWidth = (double)width;
    mCameraViewHeight = (double)height;
    mCameraViewArea = mCameraViewWidth * mCameraViewHeight;
  }


  @Override
  public void onCameraViewStopped() {
  }


  @Override
  public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
    Mat rgba = inputFrame.rgba();
    mDetector.process(rgba);
    List<MatOfPoint> contours = mDetector.getContours();
    Imgproc.drawContours(rgba, contours, -1, CONTOUR_COLOR);

    // Find the center of the cone.
    double[] coneResult = new double[3];
    final boolean coneFound = findCone(contours, MIN_SIZE_PERCENTAGE, coneResult);
    final double leftRightLocation = coneResult[0]; // -1 for left ...  1 for right
    final double topBottomLocation = coneResult[1]; // 1 for top ... 0 for bottom
    final double sizePercentage = coneResult[2];
    if (coneFound) {
      // Draw a circle on the screen at the center.
      double coneCenterX = topBottomLocation * mCameraViewWidth;
      double coneCenterY = (leftRightLocation + 1.0) / 2.0 * mCameraViewHeight;
      Core.circle(rgba, new Point(coneCenterX, coneCenterY), 5, CONTOUR_COLOR, -1);
    }
    runOnUiThread(new Runnable() {
      public void run() {
        onImageRecComplete(coneFound, leftRightLocation, topBottomLocation, sizePercentage);
      }
    });
    return rgba;
  }

  
  /**
   * Performs the math to find the leftRightLocation, topBottomLocation, and sizePercentage values.
   *
   * @param contours List of matrices containing points that match the target color.
   * @param minSizePercentage Minimum size percentage needed to call a blob a match. 0.005 would be 0.5%
   * @param coneResult Array that will be populated with the results of this math.
   * @return True if a cone is found, False if no cone is found.
   */
  private boolean findCone(List<MatOfPoint> contours, double minSizePercentage, double[] coneResult) {
    // Step #0: Determine if any contour regions were found that match the target color criteria.
    if (contours.size() == 0) {
      return false; // No contours found.
    }
    
    // Step #1: Use only the largest contour. Other contours (potential other cones) will be ignored.
    MatOfPoint contour = contours.get(0);
    double largestArea = Imgproc.contourArea(contour);
    for (int i = 1; i < contours.size(); ++i) {
      MatOfPoint currentContour = contours.get(0);
      double currentArea = Imgproc.contourArea(currentContour);
      if (largestArea < currentArea) {
        largestArea = currentArea;
        contour = currentContour;
      }
    }
    
    // Step #2: Determine if this target meets the size requirement.
    double sizePercentage = largestArea / mCameraViewArea;
    if (sizePercentage < minSizePercentage) {
      return false; // No cone found meeting the size requirement.
    }

    // Step #3: Calculate the center of the blob.
    Moments moments = Imgproc.moments(contour, false);
    double aveX = moments.get_m10() / moments.get_m00();
    double aveY = moments.get_m01() / moments.get_m00();

    // Step #4: Convert the X and Y values into leftRight and topBottom values.
    // X is 0 on the left (which is really the bottom) divide by width to scale the topBottomLocation
    // Y is 0 on the top of the view (object is left of the robot) divide by height to scale
    double leftRightLocation = aveY / (mCameraViewHeight / 2.0) - 1.0;
    double topBottomLocation = aveX / mCameraViewWidth;
    
    // Step #5: Populate the results array.
    coneResult[0] = leftRightLocation;
    coneResult[1] = topBottomLocation;
    coneResult[2] = sizePercentage;
    return true;
  }
}


