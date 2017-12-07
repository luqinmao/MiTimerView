package com.lqm.mitimerview;

import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * user：lqm
 * desc：仿小米计时器
 */
public class TimerView extends View {

    //弧度转角度
    private static final double RADIAN = 180 / Math.PI;

    private Paint mLightPaint;
    private Paint mDarkPaint;
    private int mCenterX;
    private int mCenterY;
    private String mTimeText = "00:00:00";
    private Rect mTextRect;
    private int mRadius;
    private float mScaleLength; //刻度线长度

    /* 加一个默认的padding值，为了防止用camera旋转时钟时造成四周超出view大小 */
    private float mDefaultPadding;
    private float mPaddingLeft;
    private float mPaddingTop;
    private float mPaddingRight;
    private float mPaddingBottom;

    private int mDarkColor,mLightColor;
    private Bitmap mSeekBitmap;
    private float mSeekRadius;
    private Rect mSeekBitmapRect;


    private int mState; //按钮状态
    /* 默认，停止 */
    private static final int STATE_DEFAULT = 0x00;
    /* 拖拽 */
    private static final int STATE_MOVE = 0x01;
    private int mSeekPointX; //Seek按钮X坐标
    private int mSeekPointY;

    /* 触摸时作用在Camera的矩阵 */
    private Matrix mCameraMatrix;
    /* 照相机，用于旋转时钟实现3D效果 */
    private Camera mCamera;
    /* camera绕X轴旋转的角度 */
    private float mCameraRotateX;
    /* camera绕Y轴旋转的角度 */
    private float mCameraRotateY;

    /* 指针的在x轴的位移 */
    private float mCanvasTranslateX;
    /* 指针的在y轴的位移 */
    private float mCanvasTranslateY;
    /* camera旋转的最大角度 */
    private float mMaxCameraRotate = 10;
    /* 指针的最大位移 */
    private float mMaxCanvasTranslate;
    private ValueAnimator mShakeAnim;


    public TimerView(Context context) {
        this(context,null);
    }

    public TimerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public TimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {

        mDarkColor = getResources().getColor(R.color.darkWhite);
        mLightColor = getResources().getColor(R.color.lightWhite);
        mSeekBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_seek);

        mLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLightPaint.setColor(mLightColor);
        mLightPaint.setStrokeWidth(10);
        mLightPaint.setStyle(Paint.Style.FILL);
        mLightPaint.setTextSize(50);

        mDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDarkPaint.setColor(mDarkColor);
        mDarkPaint.setStrokeWidth(10);
        mDarkPaint.setStyle(Paint.Style.STROKE);

        mTextRect = new Rect();
        mSeekBitmapRect = new Rect();
        mCameraMatrix = new Matrix();
        mCamera = new Camera();


    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mCenterX = w/2;
        mCenterY = h/2;

        //宽和高分别去掉padding值，取min的一半即表盘的半径
        mRadius = Math.min(w - getPaddingLeft() - getPaddingRight(),
                h - getPaddingTop() - getPaddingBottom()) / 2;

        mDefaultPadding = 0.12f * mRadius;//根据比例确定默认padding大小
        mPaddingLeft = mDefaultPadding + w / 2 - mRadius + getPaddingLeft();
        mPaddingTop = mDefaultPadding + h / 2 - mRadius + getPaddingTop();
        mPaddingRight = mPaddingLeft;
        mPaddingBottom = mPaddingTop;

        mScaleLength = 0.12f * mRadius;//根据比例确定刻度线长度
        mSeekRadius = (float) (mRadius*0.85);

        mSeekPointX = getWidth() / 2;
        mSeekPointY = (int) (mRadius-mSeekRadius);

        mMaxCanvasTranslate = 0.02f * mRadius;

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        setCameraRotate(canvas);

        canvas.drawColor(getResources().getColor(R.color.bgColor));
        //文字
        mLightPaint.getTextBounds(mTimeText, 0, mTimeText.length(), mTextRect);
        canvas.drawText(mTimeText,mCenterX-mTextRect.width()/2,mCenterY,mLightPaint);
        //暗色刻度线圆圈和亮色进度刻度线圆弧
        drawScaleLine(canvas);
        //最外层可滑的圆圈SeekBar
        drawScrollBar(canvas);


    }

    /**
     * 设置3D时钟效果，触摸矩阵的相关设置、照相机的旋转大小
     * 应用在绘制图形之前，否则无效
     */
    private void setCameraRotate(Canvas canvas) {
        mCameraMatrix.reset();
        mCamera.save();
        mCamera.rotateX(mCameraRotateX);//绕x轴旋转角度
        mCamera.rotateY(mCameraRotateY);//绕y轴旋转角度
        mCamera.getMatrix(mCameraMatrix);//相关属性设置到matrix中
        mCamera.restore();
        //camera在view左上角那个点，故旋转默认是以左上角为中心旋转
        //故在动作之前pre将matrix向左移动getWidth()/2长度，向上移动getHeight()/2长度
        mCameraMatrix.preTranslate(-getWidth() / 2, -getHeight() / 2);
        //在动作之后post再回到原位
        mCameraMatrix.postTranslate(getWidth() / 2, getHeight() / 2);
        canvas.concat(mCameraMatrix);//matrix与canvas相关联
    }

    /**
     * 画一圈刻度线圆圈，重绘时不断旋转画布
     */
    private void drawScaleLine(Canvas canvas) {
        canvas.save();
        mDarkPaint.setStrokeWidth(0.012f * mRadius);
        mLightPaint.setStrokeWidth(0.012f * mRadius);
        for (int i = 0; i < 200; i++) {  //暗色圆圈
            canvas.drawLine(
                    getWidth() / 2,
                    mPaddingTop + mScaleLength + mTextRect.height() / 2,
                    getWidth() / 2,
                    mPaddingTop + 2 * mScaleLength + mTextRect.height() / 2,
                    mDarkPaint);
            canvas.rotate(1.8f, getWidth() / 2, getHeight() / 2);
        }

        //根据旋转的角度mDegrees 占圆的比例计算需要画的线条范围
        double aSize = Math.asin((double) (mSeekPointX-mRadius)/mSeekRadius); //求出弧度
        int mDegrees = (int) (RADIAN*aSize); //弧度值转为角度值
        int mLineLength = (int)((((double)mDegrees/360 )* ((double)360/1.8)));
        for (int i = 0; i < mLineLength; i++) { //亮色圆弧
            canvas.drawLine(
                    getWidth() / 2,
                    mPaddingTop + mScaleLength + mTextRect.height() / 2,
                    getWidth() / 2,
                    mPaddingTop + 2 * mScaleLength + mTextRect.height() / 2,
                    mLightPaint);
            canvas.rotate(1.8f, getWidth() / 2, getHeight() / 2);
        }
        canvas.restore();
    }

    /**
     * 调节时间的圆形SeekBar
     */
    private void drawScrollBar(Canvas canvas) {
        canvas.save();
        //圆圈线
        canvas.drawCircle(mCenterX,mCenterY, mSeekRadius,mDarkPaint);
        //按钮
        mSeekBitmapRect.set(
                mSeekPointX-mSeekBitmap.getWidth()/2,
                (int) mSeekPointY - mSeekBitmap.getHeight()/2,
                mSeekPointX+mSeekBitmap.getWidth()/2,
                mSeekPointY +mSeekBitmap.getHeight()/2
        );
        canvas.drawBitmap(mSeekBitmap,mSeekBitmapRect.left,mSeekBitmapRect.top,mDarkPaint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mState = mSeekBitmapRect.contains(x,y)?STATE_MOVE:STATE_DEFAULT;

                if (mState == STATE_DEFAULT){
                    getCameraRotate(event);
                    getCanvasTranslate(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mState == STATE_MOVE){

                    calculateSeekPoint(event);

                }else{
                    //根据手指坐标计算camera应该旋转的大小
                    getCameraRotate(event);
                    getCanvasTranslate(event);
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                //松开手指，时钟复原并伴随晃动动画
                if (mState == STATE_DEFAULT){
                    startShakeAnim();
                }

                break;
        }
        return true;
    }

    /**
     * 获取camera旋转的大小
     *
     * @param event motionEvent
     */
    private void getCameraRotate(MotionEvent event) {
        float rotateX = -(event.getY() - getHeight() / 2);
        float rotateY = (event.getX() - getWidth() / 2);
        //求出此时旋转的大小与半径之比
        float[] percentArr = getPercent(rotateX, rotateY);
        //最终旋转的大小按比例匀称改变
        mCameraRotateX = percentArr[0] * mMaxCameraRotate;
        mCameraRotateY = percentArr[1] * mMaxCameraRotate;
    }

    /**
     * 当拨动时钟时，会发现时针、分针、秒针和刻度盘会有一个较小的偏移量，形成近大远小的立体偏移效果
     * 一开始我打算使用 matrix 和 camera 的 mCamera.translate(x, y, z) 方法改变 z 的值
     * 但是并没有效果，所以就动态计算距离，然后在 onDraw()中分零件地 mCanvas.translate(x, y)
     *
     * @param event motionEvent
     */
    private void getCanvasTranslate(MotionEvent event) {
        float translateX = (event.getX() - getWidth() / 2);
        float translateY = (event.getY() - getHeight() / 2);
        //求出此时位移的大小与半径之比
        float[] percentArr = getPercent(translateX, translateY);
        //最终位移的大小按比例匀称改变
        mCanvasTranslateX = percentArr[0] * mMaxCanvasTranslate;
        mCanvasTranslateY = percentArr[1] * mMaxCanvasTranslate;
    }


    /**
     * 获取一个操作旋转或位移大小的比例
     *
     * @param x x大小
     * @param y y大小
     * @return 装有xy比例的float数组
     */
    private float[] getPercent(float x, float y) {
        float[] percentArr = new float[2];
        float percentX = x / mRadius;
        float percentY = y / mRadius;
        if (percentX > 1) {
            percentX = 1;
        } else if (percentX < -1) {
            percentX = -1;
        }
        if (percentY > 1) {
            percentY = 1;
        } else if (percentY < -1) {
            percentY = -1;
        }
        percentArr[0] = percentX;
        percentArr[1] = percentY;
        return percentArr;
    }

    /**
     * 时钟晃动动画
     */
    private void startShakeAnim() {
        final String cameraRotateXName = "cameraRotateX";
        final String cameraRotateYName = "cameraRotateY";
        final String canvasTranslateXName = "canvasTranslateX";
        final String canvasTranslateYName = "canvasTranslateY";
        PropertyValuesHolder cameraRotateXHolder =
                PropertyValuesHolder.ofFloat(cameraRotateXName, mCameraRotateX, 0);
        PropertyValuesHolder cameraRotateYHolder =
                PropertyValuesHolder.ofFloat(cameraRotateYName, mCameraRotateY, 0);
        PropertyValuesHolder canvasTranslateXHolder =
                PropertyValuesHolder.ofFloat(canvasTranslateXName, mCanvasTranslateX, 0);
        PropertyValuesHolder canvasTranslateYHolder =
                PropertyValuesHolder.ofFloat(canvasTranslateYName, mCanvasTranslateY, 0);
        mShakeAnim = ValueAnimator.ofPropertyValuesHolder(cameraRotateXHolder,
                cameraRotateYHolder, canvasTranslateXHolder, canvasTranslateYHolder);
        mShakeAnim.setInterpolator(new TimeInterpolator() {
            @Override
            public float getInterpolation(float input) {
                //http://inloop.github.io/interpolator/
                float f = 0.571429f;
                return (float) (Math.pow(2, -2 * input) * Math.sin((input - f / 4) * (2 * Math.PI) / f) + 1);
            }
        });
        mShakeAnim.setDuration(1000);
        mShakeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCameraRotateX = (float) animation.getAnimatedValue(cameraRotateXName);
                mCameraRotateY = (float) animation.getAnimatedValue(cameraRotateYName);
                mCanvasTranslateX = (float) animation.getAnimatedValue(canvasTranslateXName);
                mCanvasTranslateY = (float) animation.getAnimatedValue(canvasTranslateYName);

                invalidate();
            }
        });
        mShakeAnim.start();
    }

    /**
     * 计算SeekBitmap的位置
     * @param event
     */
    private void calculateSeekPoint(MotionEvent event) {

        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean isLeft = x - mRadius < 0;
        boolean isTop = y - mRadius < 0;
        if (isLeft && isTop) { //左上
            mSeekPointX = x;
            mSeekPointY = mRadius - (int) Math.sqrt(mSeekRadius * mSeekRadius
                    - (mRadius - x) * (mRadius - x));

        } else if (isLeft && !isTop) { //左下
            mSeekPointX = x;
            mSeekPointY = mRadius + (int) Math.sqrt(mSeekRadius * mSeekRadius
                    - (mRadius - x) * (mRadius - x));

        } else if (!isLeft && isTop) { //右上
            mSeekPointX = x;
            mSeekPointY = mRadius - (int) Math.sqrt(mSeekRadius * mSeekRadius
                    - (x - mRadius) * (x - mRadius));

        } else if (!isLeft && !isTop) { //右下
            mSeekPointX = x;
            mSeekPointY = mRadius + (int) Math.sqrt(mSeekRadius * mSeekRadius
                    - (x - mRadius) * (x - mRadius));
        }
        Log.e("aaa","mSeekPointX："+mSeekPointX+"-----mSeekPointY:"+mSeekPointY);
    }
}
