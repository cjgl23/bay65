package app.nook.tv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

final class SystemIcon extends Drawable {
    private final String kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    SystemIcon(String kind,int color){this.kind=kind;this.color=color;}
    @Override public void draw(Canvas canvas){
        canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        paint.setColor(color);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);
        if(kind.equals("settings")){
            canvas.drawCircle(12,12,6.6f,paint);canvas.drawCircle(12,12,2.7f,paint);
            for(int i=0;i<8;i++){double angle=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(angle)*7,12+(float)Math.sin(angle)*7,12+(float)Math.cos(angle)*9.6f,12+(float)Math.sin(angle)*9.6f,paint);}
        }else if(kind.equals("ethernet")){
            canvas.drawRoundRect(5,3,19,14,1,1,paint);canvas.drawLine(8,3,8,7,paint);canvas.drawLine(12,3,12,7,paint);canvas.drawLine(16,3,16,7,paint);canvas.drawLine(12,14,12,21,paint);canvas.drawLine(7,21,17,21,paint);
        }else{
            Path path=new Path();path.moveTo(2,8);path.quadTo(12,0,22,8);canvas.drawPath(path,paint);path.reset();path.moveTo(5.5f,12);path.quadTo(12,6,18.5f,12);canvas.drawPath(path,paint);path.reset();path.moveTo(9,16);path.quadTo(12,13,15,16);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(12,20,1.2f,paint);
            if(kind.equals("offline")){paint.setStyle(Paint.Style.STROKE);canvas.drawLine(3,22,21,3,paint);}
        }
        canvas.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
