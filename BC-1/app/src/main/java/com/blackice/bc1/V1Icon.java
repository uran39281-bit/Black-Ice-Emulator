package com.blackice.bc1;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small monoline controls matching the approved V1 editor reference. */
final class V1Icon extends Drawable {
    private final String name;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    V1Icon(String name,int color){this.name=name;pen.setColor(color);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.6f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
    private void line(float x,float y,float xx,float yy){path.moveTo(x,y);path.lineTo(xx,yy);}
    @Override public void draw(Canvas canvas){
        canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);path.reset();
        switch(name){
            case "Media":path.addRect(3,5,21,20,Path.Direction.CW);line(3,5,9,5);line(9,5,11,2);line(11,2,17,2);line(17,2,19,5);line(5,16,10,11);line(10,11,14,15);line(14,15,17,12);line(17,12,20,16);break;
            case "Edit":case "Split":path.addCircle(6,17,3,Path.Direction.CW);path.addCircle(18,17,3,Path.Direction.CW);line(8,15,20,3);line(16,15,4,3);break;
            case "Effects":path.moveTo(12,2);path.lineTo(15,9);path.lineTo(22,12);path.lineTo(15,15);path.lineTo(12,22);path.lineTo(9,15);path.lineTo(2,12);path.lineTo(9,9);path.close();break;
            case "Layers":path.moveTo(12,3);path.lineTo(22,8);path.lineTo(12,13);path.lineTo(2,8);path.close();path.moveTo(2,13);path.lineTo(12,18);path.lineTo(22,13);path.moveTo(2,18);path.lineTo(12,23);path.lineTo(22,18);break;
            case "Setup":line(3,6,21,6);line(3,12,21,12);line(3,18,21,18);path.addCircle(8,6,2,Path.Direction.CW);path.addCircle(16,12,2,Path.Direction.CW);path.addCircle(10,18,2,Path.Direction.CW);break;
            case "Trim":line(5,3,5,21);line(19,3,19,21);line(9,6,9,18);line(9,6,15,6);line(9,18,15,18);break;
            case "Duplicate":path.addRect(8,8,21,21,Path.Direction.CW);path.moveTo(16,4);path.lineTo(3,4);path.lineTo(3,17);break;
            case "Opacity":path.addCircle(12,12,9,Path.Direction.CW);line(12,3,12,21);for(int y=6;y<=18;y+=3)line(12,y,18,y);break;
            case "Chroma Key":path.addRoundRect(3,3,21,21,3,3,Path.Direction.CW);path.addCircle(12,9,3,Path.Direction.CW);path.moveTo(7,20);path.lineTo(8,15);path.lineTo(16,15);path.lineTo(17,20);break;
            case "Extract Audio":path.moveTo(7,18);path.lineTo(7,5);path.lineTo(16,3);path.lineTo(16,12);path.addOval(2,16,7,21,Path.Direction.CW);line(13,18,22,18);line(19,15,22,18);line(22,18,19,21);break;
            case "Volume":path.moveTo(3,9);path.lineTo(7,9);path.lineTo(12,4);path.lineTo(12,20);path.lineTo(7,15);path.lineTo(3,15);path.close();path.addArc(10,6,20,18,-60,120);path.addArc(8,2,25,22,-55,110);break;
            case "Delete":line(3,6,21,6);line(9,3,15,3);path.moveTo(6,6);path.lineTo(7,21);path.lineTo(17,21);path.lineTo(18,6);line(10,10,10,17);line(14,10,14,17);break;
            case "Save":path.addRect(4,3,21,21,Path.Direction.CW);path.addRect(8,3,17,9,Path.Direction.CW);path.addRect(8,14,17,21,Path.Direction.CW);break;
            case "Undo":line(4,5,4,12);line(4,12,11,12);path.moveTo(4,12);path.cubicTo(6,3,21,5,20,14);path.cubicTo(20,18,16,20,12,20);break;
            case "Redo":line(20,5,20,12);line(20,12,13,12);path.moveTo(20,12);path.cubicTo(18,3,3,5,4,14);path.cubicTo(4,18,8,20,12,20);break;
            default:path.addCircle(12,12,8,Path.Direction.CW);break;
        }
        canvas.drawPath(path,pen);canvas.restore();
    }
    @Override public void setAlpha(int alpha){pen.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);invalidateSelf();}
    @Override public void setTint(int color){pen.setColor(color);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
