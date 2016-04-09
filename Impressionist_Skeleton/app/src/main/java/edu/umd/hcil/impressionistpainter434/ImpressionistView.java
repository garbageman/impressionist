package edu.umd.hcil.impressionistpainter434;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.MessageFormat;
import java.util.Random;

/**
 * Created by Damien :D on 3/20/2016.
 */
public class ImpressionistView extends View {

    private ImageView _imageView;
    private Random _rand = null;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();

    private int _alpha = 150;
    private int _defaultRadius = 5;
    private int _squareWidth = 10;
    private Point _lastPoint = null;
    private long _lastPointTime = -1;
    private boolean _useMotionSpeedForBrushStrokeSize = true;
    private Paint _paintBorder = new Paint();
    private BrushType _brushType = BrushType.Square;
    private float _minBrushRadius = 5;
    private VelocityTracker _tracker = null;

    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setStrokeWidth(4);

        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(50);

        _tracker = VelocityTracker.obtain();
        _rand = new Random();

        //_paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView){
        _imageView = imageView;
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        _brushType = brushType;
    }


    public Bitmap getBitmap() {
        return this._offScreenBitmap;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        //TODO
        _offScreenBitmap = Bitmap.createBitmap(_imageView.getWidth(), _imageView.getHeight(), Bitmap.Config.ARGB_8888);
        _offScreenCanvas = new Canvas(_offScreenBitmap);
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(_offScreenBitmap != null) {
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);

    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){

        //TODO
        //Basically, the way this works is to listen for Touch Down and Touch Move events and determine where those
        //touch locations correspond to the bitmap in the ImageView. You can then grab info about the bitmap--like the pixel color--
        //at that location
        Bitmap imageViewBitmap = _imageView.getDrawingCache();

        float touchX = motionEvent.getX();
        float touchY = motionEvent.getY();

        int currentPixel = imageViewBitmap.getPixel((int) touchX, (int) touchY);
        _paint.setARGB(200, Color.red(currentPixel), Color.green(currentPixel), Color.blue(currentPixel));

        _tracker.addMovement(motionEvent);
        _tracker.computeCurrentVelocity(1000);

        /* Use the velocity to influence the size */
        int normalizedVelocity = (int)Math.sqrt(Math.pow(_tracker.getXVelocity(), 2) + Math.pow(_tracker.getYVelocity(), 2)) / 50;

        if (normalizedVelocity < 0) {
            normalizedVelocity = 0;
        } else if (normalizedVelocity > 20) {
            normalizedVelocity = 20;
        }

        switch(motionEvent.getAction()){
            /* Down and Move should do the same thing? */
            case MotionEvent.ACTION_DOWN:
                /* Draw current point */
                drawPoint(touchX, touchY, _paint, normalizedVelocity);
                break;
            case MotionEvent.ACTION_MOVE:
                int historySize = motionEvent.getHistorySize();
                for (int i = 0; i < historySize; i++) {
                    /* Get historical x and y and draw the points to offscreen */
                    float pastTouchX = motionEvent.getHistoricalX(i);
                    float pastTouchY = motionEvent.getHistoricalY(i);

                    drawPoint(pastTouchX, pastTouchY, _paint, normalizedVelocity);
                }
                /* Draw current things
                 */
                drawPoint(touchX, touchY, _paint, normalizedVelocity);
                break;
        }

        invalidate();
        return true;

    }



    /* Handles generic painting for a particular point */
    private void drawPoint(float X, float Y, Paint p, int velocity) {
        if (_brushType == BrushType.Square) {
            /* Draw square thing */
            _offScreenCanvas.drawRect(X-5,Y+5,X+5,Y-5,_paint);
        } else if (_brushType == BrushType.Point) {
            /* Draw a pixel, maybe draw a sized rectangle */
            _offScreenCanvas.drawPoint(X,Y,_paint);
        } else if (_brushType == BrushType.Circle) {
            _offScreenCanvas.drawCircle(X, Y, _defaultRadius + velocity, _paint);
        } else if (_brushType == BrushType.SprayPaint) {
            /* Draw a random point in the radius */
            float x = genX(X);
            _offScreenCanvas.drawPoint(X + x,Y + genY(x),_paint);
        } else {
            /* Draw generic pixel? */
            _offScreenCanvas.drawPoint(X,Y,_paint);
        }
    }

    /* This generates a random x value within a radius of 10 */
    private float genX(float x) {
        return (_rand.nextFloat() * 40) - 20;
    }

    /* This uses the x value to figure out what the y value should be */
    private float genY(float x) {
        x = Math.abs(x);
        if (_rand.nextBoolean()) {
            return x * (float)Math.tan(Math.acos(x/20.0));
        } else {
            return -x * (float)Math.tan(Math.acos(x/20.0));
        }
    }

    /**
     * Returns if the X or Y is within the screen to prevent from crashing
     * @param x
     * @param y
     * @return
     */
    private boolean isOnScreen(float x, float y) {
        /* I'm not sure if it is necessary to check that the number is greater than 0 */
        return ((x < _offScreenCanvas.getWidth()) && (x > 0)) && ((y < _offScreenCanvas.getHeight()) && (y > 0));
    }

    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }
}

