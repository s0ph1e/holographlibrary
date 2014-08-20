/*
 * 	   Created by Daniel Nadeau
 * 	   daniel.nadeau01@gmail.com
 * 	   danielnadeau.blogspot.com
 * 
 * 	   Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package com.echo.holographlibrary;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;

public class PieGraph extends View implements  HoloGraphAnimate {

    private int mPadding;
    private int mInnerCircleRatio;
    private ArrayList<PieSlice> mSlices = new ArrayList<PieSlice>();
    private Paint mPaint = new Paint();
    private int mSelectedIndex = -1;
    private OnSliceClickedListener mListener;
    private boolean mDrawCompleted = false;
    private RectF mRectF = new RectF();
    private Bitmap mBackgroundImage = null;
    private Point mBackgroundImageAnchor = new Point(0,0);
    private boolean mBackgroundImageCenter = false;

    private PointF mCentralPoint = new PointF();
    private float mRadius;
    private float mTotalValue;

    private boolean mDrawLabels = false;
    private int mLabelRadius = 15;
    private int mLabelOffset = -5;

    private int mDuration = 300;//in ms
    private Interpolator mInterpolator;
    private Animator.AnimatorListener mAnimationListener;
    private ValueAnimator mValueAnimator;

    public PieGraph(Context context) {
        this(context, null);
    }

    public PieGraph(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PieGraph(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.PieGraph, 0, 0);
        mInnerCircleRatio = a.getInt(R.styleable.PieGraph_pieInnerCircleRatio, 0);
        mPadding = a.getDimensionPixelSize(R.styleable.PieGraph_pieSlicePadding, 0);
    }

    public void onDraw(Canvas canvas) {
        float innerRadius;

        canvas.drawColor(Color.TRANSPARENT);
        mPaint.reset();
        mPaint.setAntiAlias(true);

        if(mBackgroundImage != null) {
            if(mBackgroundImageCenter)
                mBackgroundImageAnchor.set(
                        getWidth() / 2 - mBackgroundImage.getWidth() / 2,
                        getHeight() / 2 - mBackgroundImage.getHeight() / 2
                );
            canvas.drawBitmap(mBackgroundImage, mBackgroundImageAnchor.x, mBackgroundImageAnchor.y, mPaint);
        }

        float currentAngle = 270;
        float currentSweep = 0;

        mCentralPoint.set(getWidth() / 2, getHeight() / 2);

        mRadius = Math.min(mCentralPoint.x, mCentralPoint.y) - mPadding;
        int labelTotalOffset = mLabelOffset + 2 * mLabelRadius;
        if(mDrawLabels && labelTotalOffset > 0) {
            mRadius -= labelTotalOffset;
        }
        innerRadius = mRadius * mInnerCircleRatio / 255;

        mTotalValue = 0;
        for (PieSlice slice : mSlices) {
            mTotalValue += slice.getValue();
        }

        int count = 0;
        for (PieSlice slice : mSlices) {
            Path p = slice.getPath();
            p.reset();

            if (mSelectedIndex == count && mListener != null) {
                mPaint.setColor(slice.getSelectedColor());
            } else {
                mPaint.setColor(slice.getColor());
            }
            currentSweep = (slice.getValue() / mTotalValue) * (360);

            mRectF.set(mCentralPoint.x - mRadius, mCentralPoint.y - mRadius, mCentralPoint.x + mRadius, mCentralPoint.y + mRadius);
            createArc(p, mRectF, currentSweep,
                    currentAngle + mPadding, currentSweep - mPadding);
            mRectF.set(mCentralPoint.x - innerRadius, mCentralPoint.y - innerRadius,
                    mCentralPoint.x + innerRadius, mCentralPoint.y + innerRadius);
            createArc(p, mRectF, currentSweep,
                    (currentAngle + mPadding) + (currentSweep - mPadding),
                    -(currentSweep - mPadding));

            p.close();

            // Create selection region
            Region r = slice.getRegion();
            r.set((int) (mCentralPoint.x - mRadius),
                    (int) (mCentralPoint.y - mRadius),
                    (int) (mCentralPoint.x + mRadius),
                    (int) (mCentralPoint.y + mRadius));
            canvas.drawPath(p, mPaint);

            // Calculate center angles for slices (need to draw labels)
            slice.setCentralAngle(currentAngle + currentSweep / 2);

            currentAngle = currentAngle + currentSweep;
            count++;
        }

        if (mDrawLabels) {
            drawLabels(canvas);
        }

        mDrawCompleted = true;
    }

    private void createArc(Path p, RectF mRectF, float currentSweep, float startAngle, float sweepAngle) {
        if (currentSweep == 360) {
            p.addArc(mRectF, startAngle, sweepAngle);
        } else {
            p.arcTo(mRectF, startAngle, sweepAngle);
        }
    }

    private void drawLabels(Canvas canvas) {
        for (PieSlice slice : mSlices) {
            int percentageValue = (int) (slice.getValue() / mTotalValue * 100);
            if (percentageValue > 0 && percentageValue < 100) {
                String textToDraw = String.format("%d%%", percentageValue);
                float cos = (float) Math.cos(Math.toRadians((double) slice.getCentralAngle()));
                float sin = (float) Math.sin(Math.toRadians((double) slice.getCentralAngle()));
                float centerX = mCentralPoint.x + (mRadius + mLabelOffset + mLabelRadius) * cos;
                float centerY = mCentralPoint.y + (mRadius + mLabelOffset + mLabelRadius) * sin;

                // Draw transparent outer circle
                mPaint.setColor(getResources().getColor(android.R.color.white));
                canvas.drawCircle(centerX, centerY, mLabelRadius + 1, mPaint);

                // Draw inner color circle
                mPaint.setColor(slice.getColor());
                canvas.drawCircle(centerX, centerY, mLabelRadius, mPaint);

                // Draw text
                mPaint.setColor(getResources().getColor(android.R.color.white));
                mPaint.setTextSize((int) (0.8 * mLabelRadius));
                mPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(textToDraw, centerX, centerY - (mPaint.descent() + mPaint.ascent()) / 2, mPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDrawCompleted) {
            Point point = new Point();
            point.x = (int) event.getX();
            point.y = (int) event.getY();

            int count = 0;
            Region r = new Region();
            for (PieSlice slice : mSlices) {
                r.setPath(slice.getPath(), slice.getRegion());
                switch (event.getAction()) {
                    default:
                        break;
                    case MotionEvent.ACTION_DOWN:
                        if (r.contains(point.x, point.y)) {
                            mSelectedIndex = count;
                            postInvalidate();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (count == mSelectedIndex
                                && mListener != null
                                && r.contains(point.x, point.y)) {
                            mListener.onClick(mSelectedIndex);
                        }
                        break;
                }
                count++;
            }
        }
        // Case we click somewhere else, also get feedback!
        if(MotionEvent.ACTION_UP == event.getAction()
                && mSelectedIndex == -1
                && mListener != null) {
            mListener.onClick(mSelectedIndex);
        }
        // Reset selection
        if (MotionEvent.ACTION_UP == event.getAction()
                || MotionEvent.ACTION_CANCEL == event.getAction()) {
            mSelectedIndex = -1;
            postInvalidate();
        }
        return true;
    }

    public Bitmap getBackgroundBitmap() {
        return mBackgroundImage;
    }

    public void setBackgroundBitmap(Bitmap backgroundBitmap, int pos_x, int pos_y) {
        mBackgroundImage = backgroundBitmap;
        mBackgroundImageAnchor.set(pos_x, pos_y);
        postInvalidate();
    }

    public void setBackgroundBitmap(Bitmap backgroundBitmap) {
        mBackgroundImageCenter = true;
        mBackgroundImage = backgroundBitmap;
        postInvalidate();
    }

    /**
     * sets padding
     * @param padding
     */
    public void setPadding(int padding) {
        mPadding = padding;
        postInvalidate();
    }

    public void setInnerCircleRatio(int innerCircleRatio) {
        mInnerCircleRatio = innerCircleRatio;
        postInvalidate();
    }

    public ArrayList<PieSlice> getSlices() {
        return mSlices;
    }

    public void setSlices(ArrayList<PieSlice> slices) {
        mSlices = slices;
        postInvalidate();
    }

    public PieSlice getSlice(int index) {
        return mSlices.get(index);
    }

    public void addSlice(PieSlice slice) {
        mSlices.add(slice);
        postInvalidate();
    }

    public void setOnSliceClickedListener(OnSliceClickedListener listener) {
        mListener = listener;
    }

    public void removeSlices() {
        mSlices.clear();
        postInvalidate();
    }

    @Override
    public int getDuration() {
        return mDuration;
    }

    @Override
    public void setDuration(int duration) {mDuration = duration;}

    @Override
    public Interpolator getInterpolator() {
        return mInterpolator;
    }

    @Override
    public void setInterpolator(Interpolator interpolator) {mInterpolator = interpolator;}


    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public boolean isAnimating() {
        if(mValueAnimator != null)
            return mValueAnimator.isRunning();
        return false;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public boolean cancelAnimating() {
        if (mValueAnimator != null)
            mValueAnimator.cancel();
        return false;
    }


    /**
     * Stops running animation and starts a new one, animating each slice from their current to goal value.
     * If removing a slice, consider animating to 0 then removing in onAnimationEnd listener.
     * Default inerpolator is linear; constant speed.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public void animateToGoalValues() {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1){
            Log.e("HoloGraphLibrary compatibility error", "Animation not supported on api level 12 and below. Returning without animating.");
            return;
        }
        if (mValueAnimator != null)
            mValueAnimator.cancel();

        for (PieSlice s : mSlices)
            s.setOldValue(s.getValue());
        ValueAnimator va = ValueAnimator.ofFloat(0,1);
        mValueAnimator = va;
        va.setDuration(getDuration());
        if (mInterpolator == null) mInterpolator = new LinearInterpolator();
        va.setInterpolator(mInterpolator);
        if (mAnimationListener != null) va.addListener(mAnimationListener);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float f = Math.max(animation.getAnimatedFraction(), 0.01f);//avoid blank frames; never multiply values by 0
               // Log.d("f", String.valueOf(f));
                for (PieSlice s : mSlices) {
                    float x = s.getGoalValue() - s.getOldValue();
                    s.setValue(s.getOldValue() + (x * f));
                }
                postInvalidate();
            }});
            va.start();

    }

    @Override
    public void setAnimationListener(Animator.AnimatorListener animationListener) { mAnimationListener = animationListener;}

    public interface OnSliceClickedListener {
        public abstract void onClick(int index);
    }

    public boolean getDrawLabels() {
        return mDrawLabels;
    }

    public void setDrawLabels(boolean drawLabels) {
        mDrawLabels = drawLabels;
        postInvalidate();
    }

    public int getLabelRadius() {
        return mLabelRadius;
    }

    public void setLabelRadius(int labelRadius) {
        mLabelRadius = labelRadius;
        postInvalidate();
    }

    public int getLabelOffset() {
        return mLabelOffset;
    }

    public void setLabelOffset(int labelOffset) {
        mLabelOffset = labelOffset;
        postInvalidate();
    }
}
