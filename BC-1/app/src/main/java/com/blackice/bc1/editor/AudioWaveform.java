package com.blackice.bc1.editor;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.nio.*;
/** Decodes audio only on the worker executor. Waveforms are actual PCM peaks, not decoration. */
public final class AudioWaveform {
 public static boolean hasAudio(Context c,String uri)throws Exception{MediaExtractor e=new MediaExtractor();try{e.setDataSource(c,Uri.parse(uri),null);for(int i=0;i<e.getTrackCount();i++)if(e.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/"))return true;return false;}finally{e.release();}}
 public static float[] read(Context context,String uri,long duration)throws Exception{
  MediaExtractor e=new MediaExtractor();MediaCodec codec=null;float[] peaks=new float[512];
  try{e.setDataSource(context,Uri.parse(uri),null);int track=-1;MediaFormat format=null;for(int i=0;i<e.getTrackCount();i++){MediaFormat f=e.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;format=f;break;}}if(track<0)throw new IllegalArgumentException("No audio track in this source");e.selectTrack(track);
   codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();boolean inputEnd=false,outputEnd=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();int channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),encoding=2;long deadline=System.currentTimeMillis()+120000;
   while(!outputEnd){if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Waveform decoding took too long");
    if(!inputEnd){int i=codec.dequeueInputBuffer(10000);if(i>=0){ByteBuffer b=codec.getInputBuffer(i);int n=e.readSampleData(b,0);if(n<0){codec.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}else{codec.queueInputBuffer(i,0,n,e.getSampleTime(),0);e.advance();}}}
    int o=codec.dequeueOutputBuffer(info,10000);if(o==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):2;}
    if(o>=0){ByteBuffer b=codec.getOutputBuffer(o).order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size);int bytes=encoding==4?4:2,sample=0;while(b.remaining()>=bytes){float value=encoding==4?Math.abs(b.getFloat()):Math.abs((float)b.getShort()/32768);long ms=info.presentationTimeUs/1000+(sample++/channels)*1000L/rate;int bin=(int)Math.max(0,Math.min(511,ms*512/Math.max(1,duration)));peaks[bin]=Math.max(peaks[bin],value);}outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(o,false);}
   }return peaks;
  }finally{if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}e.release();}
 }
}
