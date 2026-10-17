package com.blackice.bc1.editor;
import java.util.*;
/** Source-time keyframes: trimming and splitting do not move animation. */
public final class Motion {
 public static final String[] PROPERTIES={"X","Y","Scale","Rotation","Opacity","Intensity","Skew X","Skew Y","Anchor X","Anchor Y"};
 public static final int X=0,Y=1,SCALE=2,ROTATION=3,OPACITY=4,INTENSITY=5,SKEW_X=6,SKEW_Y=7,ANCHOR_X=8,ANCHOR_Y=9,COUNT=10;
 public static final class Key {
  public long timeMs; public float value,x1=.25f,y1=.1f,x2=.25f,y2=1;
  public Key(long time,float v){timeMs=time;value=v;}
  public Key copy(){Key k=new Key(timeMs,value);k.x1=x1;k.y1=y1;k.x2=x2;k.y2=y2;return k;}
 }
 public final List<List<Key>> curves=new ArrayList<>();
 public Motion(){for(int i=0;i<COUNT;i++)curves.add(new ArrayList<>());}
 public Motion copy(){Motion m=new Motion();for(int i=0;i<COUNT;i++)for(Key k:curves.get(i))m.curves.get(i).add(k.copy());return m;}
 public void put(int property,Key key){List<Key> keys=curves.get(property);keys.removeIf(k->k.timeMs==key.timeMs);keys.add(key);keys.sort(Comparator.comparingLong(k->k.timeMs));}
 public boolean animated(){for(List<Key> keys:curves)if(!keys.isEmpty())return true;return false;}
 public float value(int property,long time,float fallback){List<Key> keys=curves.get(property);if(keys.isEmpty())return fallback;Key a=keys.get(0);if(time<=a.timeMs)return a.value;
  for(int i=1;i<keys.size();i++){Key b=keys.get(i);if(time<=b.timeMs){float t=(float)(time-a.timeMs)/(b.timeMs-a.timeMs);return a.value+(b.value-a.value)*ease(t,a);}a=b;}return a.value;
 }
 private static float bez(float t,float a,float b){float q=1-t;return 3*q*q*t*a+3*q*t*t*b+t*t*t;}
 public static float ease(float x,Key k){float low=0,high=1;for(int i=0;i<20;i++){float t=(low+high)*.5f;if(bez(t,k.x1,k.x2)<x)low=t;else high=t;}return bez((low+high)*.5f,k.y1,k.y2);}
 public static boolean allowed(int p,float v){return Float.isFinite(v)&&(p<2||p>=8?Math.abs(v)<=2:p==2?v>=.05f&&v<=4:p==3?Math.abs(v)<=3600:p>=6?Math.abs(v)<=80:v>=0&&v<=1);}
 public void validate(){for(int p=0;p<COUNT;p++){List<Key> keys=curves.get(p);if(keys.size()>200)throw new IllegalArgumentException("Use at most 200 keys per property.");long previous=-1;for(Key k:keys){if(k.timeMs<0||k.timeMs<=previous||!allowed(p,k.value)||!unit(k.x1)||!unit(k.x2)||!unit(k.y1)||!unit(k.y2))throw new IllegalArgumentException("Invalid motion curve.");previous=k.timeMs;}}}
 private static boolean unit(float f){return Float.isFinite(f)&&f>=0&&f<=1;}
}
