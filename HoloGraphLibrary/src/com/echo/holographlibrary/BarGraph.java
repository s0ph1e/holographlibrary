/*
 *     Created by Daniel Nadeau
 *     daniel.nadeau01@gmail.com
 *     danielnadeau.blogspot.com
 * 
 *     Licensed to the Apache Software Foundation (ASF) under one
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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.NinePatchDrawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

public class BarGraph extends View {

    private final static int VALUE_FONT_SIZE = 30;
    private final static int AXIS_LABEL_FONT_SIZE = 15;
    private final static float LABEL_PADDING_MULTIPLIER = 1.6f; //how much space to leave between labels when shrunken. Increase for less space.
    private final static int ORIENTATION_HORIZONTAL = 0;
    private final static int ORIENTATION_VERTICAL = 1;

    private final int mOrientation;
    private ArrayList<Bar> mBars = new ArrayList<Bar>();
    private Paint mPaint = new Paint();
    private Rect mBoundsRect = new Rect();
    private Rect mTextRect = new Rect();
    private boolean mShowBarText = true;
    private boolean mShowAxis = true;
    private int mSelectedIndex = -1;
    private OnBarClickedListener mListener;
    private Bitmap mFullImage;
    private boolean mShouldUpdate = false;

    public BarGraph(Context context) {
        this(context, null);
    }
    
    public BarGraph(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BarGraph(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.BarGraph);
        mOrientation = a.getInt(R.styleable.BarGraph_orientation, ORIENTATION_VERTICAL);
    }

    public void setShowBarText(boolean show){
        mShowBarText = show;
    }
    
    public void setShowAxis(boolean show){
        mShowAxis = show;
    }
    
    public void setBars(ArrayList<Bar> points){
        this.mBars = points;
        mShouldUpdate = true;
        postInvalidate();
    }
    
    public ArrayList<Bar> getBars(){
        return this.mBars;
    }

    public void onDraw(Canvas ca) {
    	
        if (mFullImage == null || mShouldUpdate) {
            final Resources resources = getContext().getResources();

            mFullImage = Bitmap.createBitmap(getWidth(), getHeight(), Config.ARGB_8888);
            Canvas canvas = new Canvas(mFullImage);
            canvas.drawColor(Color.TRANSPARENT);
            NinePatchDrawable popup = (NinePatchDrawable)this.getResources().getDrawable(R.drawable.popup_black);
            
            float maxValue = 0;
            float padding = 7 * getContext().getResources().getDisplayMetrics().density;
            float bottomPadding = 30 * resources.getDisplayMetrics().density;
            
            float usableHeight;
            if (mShowBarText) {
                this.mPaint.setTextSize(VALUE_FONT_SIZE * resources.getDisplayMetrics().scaledDensity);
                this.mPaint.getTextBounds("$", 0, 1, mTextRect);
                usableHeight = getHeight()-bottomPadding-Math.abs(mTextRect.top-mTextRect.bottom)
                        -24 * resources.getDisplayMetrics().density;
            } else {
                usableHeight = getHeight()-bottomPadding;
            }
             
            // Draw x-axis line
            if (mShowAxis){
                mPaint.setColor(Color.BLACK);
                mPaint.setStrokeWidth(2 * resources.getDisplayMetrics().density);
                mPaint.setAlpha(50);
                mPaint.setAntiAlias(true);
                canvas.drawLine(0,
                        getHeight()-bottomPadding+10* resources.getDisplayMetrics().density,
                        getWidth(),
                        getHeight()-bottomPadding+10* resources.getDisplayMetrics().density, mPaint);
            }
            float barWidth = (getWidth() - (padding*2)*mBars.size())/mBars.size();

            // Maximum y value = sum of all values.
            for (final Bar bar : mBars) {
                if (bar.getValue() > maxValue) {
                    maxValue = bar.getValue();
                }
            }
            if (maxValue == 0) {
                maxValue = 1;
            }
            
            int count = 0;
            SparseArray<Float> valueTextSizes = new SparseArray<Float>();
            for (final Bar bar : mBars) {
                // Set bar bounds
                int left = (int)((padding*2)*count + padding + barWidth*count);
                int top = (int)(getHeight()-bottomPadding-(usableHeight*(bar.getValue()/maxValue)));
                int right = (int)((padding*2)*count + padding + barWidth*(count+1));
                int bottom = (int)(getHeight()-bottomPadding);
                mBoundsRect.set(left, top, right, bottom);

                // Draw bar
                if(count == mSelectedIndex && null != mListener) {
                    this.mPaint.setColor(bar.getSelectedColor());
                }
                else {
                    this.mPaint.setColor(bar.getColor());
                }
                canvas.drawRect(mBoundsRect, this.mPaint);

                // Create selection region
                bar.getPath().reset();
                bar.getPath().addRect(new RectF(mBoundsRect.left,
                                mBoundsRect.top,
                                mBoundsRect.right,
                                mBoundsRect.bottom),
                        Path.Direction.CW
                );
                bar.getRegion().set(mBoundsRect.left,
                        mBoundsRect.top,
                        mBoundsRect.right,
                        mBoundsRect.bottom);

                // Draw x-axis label text
                if (mShowAxis){
                    this.mPaint.setColor(bar.getLabelColor());
                    this.mPaint.setTextSize(AXIS_LABEL_FONT_SIZE * resources.getDisplayMetrics().scaledDensity);
                    float textWidth = this.mPaint.measureText(bar.getName());
                    while (right - left + (padding *LABEL_PADDING_MULTIPLIER)< textWidth) {
                        //decrease text size to fit and not overlap with other labels.
                        this.mPaint.setTextSize(this.mPaint.getTextSize() -  1);
                        textWidth = this.mPaint.measureText(bar.getName());
                    }
                    int x = (int)(((mBoundsRect.left+ mBoundsRect.right)/2)-(textWidth/2));
                    int y = (int) (getHeight()-3 * resources.getDisplayMetrics().scaledDensity);
                    canvas.drawText(bar.getName(), x, y, this.mPaint);
                }

                // Draw value text
                if (mShowBarText){
                    this.mPaint.setTextSize(VALUE_FONT_SIZE * resources.getDisplayMetrics().scaledDensity);
                    this.mPaint.setColor(Color.WHITE);
                    this.mPaint.getTextBounds(bar.getValueString(), 0, 1, mTextRect);
                    
                    int boundLeft = (int) (((mBoundsRect.left+ mBoundsRect.right)/2)
                            -(this.mPaint.measureText(bar.getValueString())/2)-10 * resources.getDisplayMetrics().density);
                    int boundTop = (int) (mBoundsRect.top+(mTextRect.top-mTextRect.bottom)
                            -18 * resources.getDisplayMetrics().density);
                    int boundRight = (int)(((mBoundsRect.left+ mBoundsRect.right)/2)
                            +(this.mPaint.measureText(bar.getValueString())/2)
                            +10 * resources.getDisplayMetrics().density);

                    if (boundLeft < mBoundsRect.left) boundLeft = mBoundsRect.left - ((int)padding /2);//limit popup width to bar width
                    if (boundRight > mBoundsRect.right)boundRight = mBoundsRect.right + ((int) padding /2);

                    popup.setBounds(boundLeft, boundTop, boundRight, mBoundsRect.top);
                    popup.draw(canvas);

                    if (0 > valueTextSizes.indexOfKey(bar.getValueString().length())) {
                        //check cache to see if we've done this calculation before
                        while (this.mPaint.measureText(bar.getValueString()) > boundRight - boundLeft)
                            this.mPaint.setTextSize(this.mPaint.getTextSize() - (float)1);
                        valueTextSizes.put(bar.getValueString().length(), mPaint.getTextSize());
                    }
                    else {
                        this.mPaint.setTextSize(valueTextSizes.get(bar.getValueString().length()));
                    }
                    canvas.drawText(bar.getValueString(),
                            (int) (((mBoundsRect.left + mBoundsRect.right) / 2)
                                    - (this.mPaint.measureText(bar.getValueString())) / 2),
                            mBoundsRect.top - (mBoundsRect.top - boundTop) / 2f
                                    + (float) Math.abs(mTextRect.top - mTextRect.bottom) / 2f * 0.7f,
                            this.mPaint
                    );
                }
                count++;
            }
            mShouldUpdate = false;
        }
        
        ca.drawBitmap(mFullImage, 0, 0, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        Point point = new Point();
        point.x = (int) event.getX();
        point.y = (int) event.getY();
        
        int count = 0;
        for (Bar bar : mBars){
            Region r = new Region();
            r.setPath(bar.getPath(), bar.getRegion());
            switch (event.getAction()) {
                default:
                    break;
                case MotionEvent.ACTION_DOWN:
                    if (r.contains(point.x, point.y)) {
                        mSelectedIndex = count;
                        mShouldUpdate = true;
                        postInvalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (r.contains(point.x, point.y) && null != mListener){
                        if (mSelectedIndex > -1){
                            mListener.onClick(mSelectedIndex);
                        }
                        mSelectedIndex = -1;
                    }
                    mShouldUpdate = true;
                    postInvalidate();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mSelectedIndex = -1;
                    postInvalidate();
                    break;
            }
            count++;
        }
        return true;
    }
    
    @Override
    protected void onDetachedFromWindow() {
    	if(mFullImage != null)
    		mFullImage.recycle();
    	
    	super.onDetachedFromWindow();
    }
    
    public void setOnBarClickedListener(OnBarClickedListener listener) {
        this.mListener = listener;
    }
    
    public interface OnBarClickedListener {
        abstract void onClick(int index);
    }
}
