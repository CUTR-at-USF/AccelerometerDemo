/*
 * Copyright (C) 2007 The Android Open Source Project
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

package edu.usf.cutr.android.accelerometer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import au.com.bytecode.opencsv.CSVWriter;

/**
 * <h3>Application that displays the values of the acceleration sensor graphically.
 * 	 Every 5 seconds.
 * 	 Battery data
 * </h3>


 */
public class AccelerometerDemoActivity extends Activity {
	
    private SensorManager mSensorManager;
    private GraphView mGraphView;
    //battery variables
    int scale = -1;
    int level = -1;
    int voltage = -1;
    int temp = -1;
    
    static boolean isAccelActive = false;
    /** The timer posts a runnable to the main thread via this handler. */
    private final Handler handler = new Handler();
    /**
     * This timer invokes periodically the checkLocationListener timer task.
     */
    private final Timer checkAccelListenerTimer = new Timer();
   
    //FileOutputStream writer = new FileOutputStream(null);
    
    private class GraphView extends View implements SensorEventListener
    {
        private Bitmap  mBitmap;
        private Paint   mPaint = new Paint();
        private Canvas  mCanvas = new Canvas();
        private Path    mPath = new Path();
        private RectF   mRect = new RectF();
        private float   mLastValues[] = new float[3*2];
        private float   mOrientationValues[] = new float[3];
        private int     mColors[] = new int[3*2];
        private float   mLastX;
        private float   mScale[] = new float[2];
        private float   mYOffset;
        private float   mMaxX;
        private float   mSpeed = 1.0f;
        private float   mWidth;
        private float   mHeight;
        
      //-----------------------------------------------------------------------------------------------------GRAPHVIEW CONTRUCTOR-----------------
        public GraphView(Context context) {
            super(context);
            mColors[0] = Color.argb(192, 255, 64, 64);
            mColors[1] = Color.argb(192, 64, 128, 64);
            mColors[2] = Color.argb(192, 64, 64, 255);
            mColors[3] = Color.argb(192, 64, 255, 255);
            mColors[4] = Color.argb(192, 128, 64, 128);
            mColors[5] = Color.argb(192, 255, 255, 64);

            mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
            mRect.set(-0.5f, -0.5f, 0.5f, 0.5f);
            mPath.arcTo(mRect, 0, 180);
        }
      //-----------------------------------------------------------------------------------------------------onSIZECHANGED-----------------
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            mCanvas.setBitmap(mBitmap);
            mCanvas.drawColor(0xFFFFFFFF);
            mYOffset = h * 0.5f;
            mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
            mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
            mWidth = w;
            mHeight = h;
            if (mWidth < mHeight) {
                mMaxX = w;
            } else {
                mMaxX = w-50;
            }
            mLastX = mMaxX;
            super.onSizeChanged(w, h, oldw, oldh);
        }
      //-----------------------------------------------------------------------------------------------------onDRAW-----------------
        @Override
        protected void onDraw(Canvas canvas) {
            synchronized (this) {
                if (mBitmap != null) {
                    final Paint paint = mPaint;
                    final Path path = mPath;
                    final int outer = 0xFFC0C0C0;
                    final int inner = 0xFFff7010;

                    if (mLastX >= mMaxX) {
                        mLastX = 0;
                        final Canvas cavas = mCanvas;
                        final float yoffset = mYOffset;
                        final float maxx = mMaxX;
                        final float oneG = SensorManager.STANDARD_GRAVITY * mScale[0];
                        paint.setColor(0xFFAAAAAA);
                        cavas.drawColor(0xFFFFFFFF);
                        cavas.drawLine(0, yoffset,      maxx, yoffset,      paint);
                        cavas.drawLine(0, yoffset+oneG, maxx, yoffset+oneG, paint);
                        cavas.drawLine(0, yoffset-oneG, maxx, yoffset-oneG, paint);
                    }
                    canvas.drawBitmap(mBitmap, 0, 0, null);

                    float[] values = mOrientationValues;
                    if (mWidth < mHeight) {
                        float w0 = mWidth * 0.333333f;
                        float w  = w0 - 32;
                        float x = w0*0.5f;
                        for (int i=0 ; i<3 ; i++) {
                            canvas.save(Canvas.MATRIX_SAVE_FLAG);
                            canvas.translate(x, w*0.5f + 4.0f);
                            canvas.save(Canvas.MATRIX_SAVE_FLAG);
                            paint.setColor(outer);
                            canvas.scale(w, w);
                            canvas.drawOval(mRect, paint);
                            canvas.restore();
                            canvas.scale(w-5, w-5);
                            paint.setColor(inner);
                            canvas.rotate(-values[i]);
                            canvas.drawPath(path, paint);
                            canvas.restore();
                            x += w0;
                        }
                    } else {
                        float h0 = mHeight * 0.333333f;
                        float h  = h0 - 32;
                        float y = h0*0.5f;
                        for (int i=0 ; i<3 ; i++) {
                            canvas.save(Canvas.MATRIX_SAVE_FLAG);
                            canvas.translate(mWidth - (h*0.5f + 4.0f), y);
                            canvas.save(Canvas.MATRIX_SAVE_FLAG);
                            paint.setColor(outer);
                            canvas.scale(h, h);
                            canvas.drawOval(mRect, paint);
                            canvas.restore();
                            canvas.scale(h-5, h-5);
                            paint.setColor(inner);
                            canvas.rotate(-values[i]);
                            canvas.drawPath(path, paint);
                            canvas.restore();
                            y += h0;
                        }
                    }

                }
            }
        }
      //-----------------------------------------------------------------------------------------------------onSENSORCHANGE-----------------
        public void onSensorChanged(SensorEvent event) {
        	
            Log.d("AccelerometerDemo", "sensor: " + event.sensor.getName() + ", x: " + event.values[0] + ", y: " + event.values[1] + ", z: " + event.values[2]);
            
            	
            synchronized (this) {
            	
                if (mBitmap != null) {
                	
                    final Canvas canvas = mCanvas;
                    final Paint paint = mPaint;
                    
                    if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                        for (int i=0 ; i<3 ; i++) {
                            mOrientationValues[i] = event.values[i];
                        }
                    } else {
                        float deltaX = mSpeed;
                        float newX = mLastX + deltaX;

                        int j = (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) ? 1 : 0;
                        
                        for (int i=0 ; i<3 ; i++) {
                            int k = i+j*3;
                            
                            final float v = mYOffset + event.values[i] * mScale[j];
                            paint.setColor(mColors[k]);
                            canvas.drawLine(mLastX, mLastValues[k], newX, v, paint);
                            mLastValues[k] = v;
                        }
                        
                        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                            mLastX += mSpeed;
                    }
                    invalidate();
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }
  //-----------------------------------------------------------------------------------------------------onCREATE-----------------
    /**
     * Initialization of the Activity after it is first created.  Must at least
     * call {@link android.app.Activity#setContentView setContentView()} to
     * describe what is to be displayed in the screen.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Be sure to call the super class.
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mGraphView = new GraphView(this);
        setContentView(mGraphView);
        
        /*
         * After 5 seconds, check every 5 seconds that accelerometer sensor is still
         * registered and spit out additional debugging info to the logs:
         */
        checkAccelListenerTimer.schedule(checkAccelerometerListener, 5000, 5000);
        
      
       
        
        
        
        alertbox("to Terminate", "terminate?");
        
        
    }
    
    //----------------------------------------------------------------------------------------------------------------------CSVFile---------------

    

  //-----------------------------------------------------------------------------------------------------TIMERTASK-----------------
    /**
     * Task invoked by a timer periodically to make sure the location listener is
     * still registered.
     */
    private TimerTask checkAccelerometerListener = new TimerTask() {
      @Override
      public void run() {
        // It's always safe to assume that if isRecording() is true, it implies
        // that onCreate() has finished.

    	  handler.post(new Runnable() {
            	  
            public void run() {
            	
            	battery();
            	
            	
            	//back on system thread
            	 if (isAccelActive == true) {
            		 
            		 Log.d("Status", "Accelerometer is active");
            		 mSensorManager.unregisterListener(mGraphView);
            		 isAccelActive=false;
            		 
            	 }//end of if
            	 if (isAccelActive == false) {
            		 
            		 
            		 mSensorManager.registerListener(mGraphView,
            	                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            	                SensorManager.SENSOR_DELAY_FASTEST);
            	        mSensorManager.registerListener(mGraphView,
            	                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            	                SensorManager.SENSOR_DELAY_FASTEST);
            	        mSensorManager.registerListener(mGraphView, 
            	                mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
            	                SensorManager.SENSOR_DELAY_FASTEST);
            	        
            	        isAccelActive=true;
            	 }//end if
            }//end of internal run
              		
            
            }//end of handler runnable
          );//close of handler runnable
    	  /**File writing**/
      	try {
			    File root = Environment.getExternalStorageDirectory();
			    if (root.canWrite()){
			        File gpxfile = new File(root, "file.txt");
			        FileWriter gpxwriter = new FileWriter(gpxfile);
			        BufferedWriter out = new BufferedWriter(gpxwriter);
			        out.write("Hello world");
			        out.close();
			        Log.d("File", "Writting");
			    }
			} catch (IOException e) {
			    Log.e("Error Error Error","Could not write file ");
			}//end of file writing
      	
      }//end of run
    };//end of timertask

  //-----------------------------------------------------------------------------------------------------ALERTBOX-----------------
   protected void alertbox(String title, String mymessage)  
    {  
    new AlertDialog.Builder(this)  
       .setMessage(mymessage)  
      .setTitle(title)
       .setCancelable(true)  
       .setNeutralButton(android.R.string.cancel,  
          new DialogInterface.OnClickListener() {  
          public void onClick(DialogInterface dialog, int whichButton){
        	  onDestroy();
          }  
          })  
       .show(); 
    }
    
   //-----------------------------------------------------------------------------------------------------onDESTROY-----------------
    protected void onDestroy()
    {
    	checkAccelerometerListener.cancel();
    	checkAccelerometerListener = null;
    	checkAccelListenerTimer.cancel();
    	checkAccelListenerTimer.purge();
    	
    	finish();
    }
    
    //-----------------------------------------------------------------------------------------------------BATTERY-----------------
    
    protected void battery(){
    	
    BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
       
      
        @Override
        public void onReceive(Context context, Intent intent) {
        	
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);//BATTERY CHARGE
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);//SCALE OF BATTERY CHARGE
            temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);//BATTERY TEMPERATURE
            voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);//BATTERY VOLTAGE
            Log.e("BatteryManager", "level is "+level+"/"+scale+", temp is "+temp+", voltage is "+voltage);         
           
        }

		
        
        
    };
    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    registerReceiver(batteryReceiver, filter);
    
}
    
  //----------------------------------------------------------------------------------------------------------------------------  
    /**Unused methods**/
    @Override
    protected void onResume() {
        super.onResume();
        
  
        //Starts the sensor reading
       
        
    }
    
    @Override
    protected void onStop() {
    	//turns off sensor
       
        super.onStop();
    }
}
    
    
