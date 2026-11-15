package app.nook.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Clips artwork explicitly and draws a contrasting focus ring above the image. */
final class AppTile extends View {
    private final Drawable art;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private final RectF bounds=new RectF();
    private final float density;
    private final boolean lightArtwork;
    AppTile(Context context,Drawable artwork,String label){
        super(context);art=artwork;density=getResources().getDisplayMetrics().density;
        lightArtwork=isLight(artwork);setFocusable(true);setClickable(true);setContentDescription(label);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
    }
    private boolean isLight(Drawable drawable){
        if(drawable==null)return false;
        Bitmap sample=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
        Rect original=new Rect(drawable.getBounds());
        try{
            Canvas canvas=new Canvas(sample);canvas.drawColor(0xff1a2922);drawable.setBounds(0,0,16,16);drawable.draw(canvas);
            double brightness=0;int count=0;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(x<3||y<3||x>12||y>12){int color=sample.getPixel(x,y);brightness+=(.2126*Color.red(color)+.7152*Color.green(color)+.0722*Color.blue(color))/255.0;count++;}
            return brightness/count>.68;
        }catch(RuntimeException unavailable){return false;}
        finally{drawable.setBounds(original);sample.recycle();}
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float inset=3*density,radius=9*density;
        bounds.set(inset,inset,getWidth()-inset,getHeight()-inset);clip.reset();clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
        canvas.save();canvas.clipPath(clip);canvas.drawColor(0xff1a2922);
        if(art!=null){int width=art.getIntrinsicWidth()>0?art.getIntrinsicWidth():320,height=art.getIntrinsicHeight()>0?art.getIntrinsicHeight():180;float scale=Math.max(bounds.width()/width,bounds.height()/height);canvas.translate(bounds.centerX()-width*scale/2,bounds.centerY()-height*scale/2);canvas.scale(scale,scale);art.setBounds(0,0,width,height);art.draw(canvas);}
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        if(hasFocus()){
            paint.setStrokeWidth(6*density);paint.setColor(lightArtwork?0xffe6f2dd:0xff102218);canvas.drawRoundRect(bounds,radius,radius,paint);
            paint.setStrokeWidth(3*density);paint.setColor(lightArtwork?0xff16543b:0xffd8f3a5);canvas.drawRoundRect(bounds,radius,radius,paint);
        }else{paint.setStrokeWidth(density);paint.setColor(0x33ffffff);canvas.drawRoundRect(bounds,radius,radius,paint);}
    }
    @Override protected void onFocusChanged(boolean focused,int direction,Rect previous){super.onFocusChanged(focused,direction,previous);invalidate();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");}
}
