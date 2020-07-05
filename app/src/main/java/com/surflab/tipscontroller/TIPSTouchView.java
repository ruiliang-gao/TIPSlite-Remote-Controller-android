package com.surflab.tipscontroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

///TouchView : touch event handler class
public class TIPSTouchView extends View {
    //motion down point x,y coords
    private float lastX =0;
    private float lastY =0;
    private Paint paint = new Paint();
    private Path path = new Path();

    public float motionY = 0; //length moved along Y axis, positive->slide down, negative->slide up
    public float motionX = 0; //length moved along X axis, positive->slide right, negative->slide left
    public void resetMotionXY()
    {
        motionY = 0;
        motionX = 0;
    }

    //GestureDetector mGestureDetector;

    public TIPSTouchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        //mGestureDetector = new GestureDetector(context, new GestureListener());

        paint.setAntiAlias(true);
        paint.setStrokeWidth(6f);
        paint.setColor(Color.BLACK);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);

    }

    public void setColor(int r, int g, int b) {
        int rgb = Color.rgb(r, g, b);
        paint.setColor(rgb);
    }

//    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
//        // event when double tap occurs
//        @Override
//        public boolean onDoubleTap(MotionEvent e) {
//            float x = e.getX();
//            float y = e.getY();
//            // clean drawing area on double tap
//            path.reset();
//            Log.d("Double Tap ", "Tapped at: (" + x + "," + y + ")");
//            return true;
//        }
//
//        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
//        {
//            Log.d("Scroll ", "distance: (" + distanceX + "," + distanceY + ")");
//            return true;
//        }
//
//
//    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float eventX = event.getX();
        float eventY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.reset();
                path.moveTo(eventX, eventY);
                //Log.d("TIPS_Motion_Down", " at: (" + eventX + "," + eventY + ")");
                lastX = eventX;
                lastY = eventY;
                return true;

            case MotionEvent.ACTION_MOVE:
                //Log.d("TIPS_Motion_Move", " to: (" + eventX + "," + eventY + ")");
                path.lineTo(eventX, eventY);
                motionX += eventX - lastX;
                motionY += eventY - lastY;
                lastX = eventX;
                lastY = eventY;
                break;

            case MotionEvent.ACTION_UP:
                //Log.d("TIPS_Motion_Up", " at: (" + eventX + "," + eventY + ")");
                //Log.d("TIPS_Motion vector", " : (" + motionX + "," + motionY + ")");
                resetMotionXY();
                break;

            default:
                return false;
        }
        // for demo purposes
        //mGestureDetector.onTouchEvent(event);
        // Schedules a repaint.
        invalidate();
        return true;
    }
}

