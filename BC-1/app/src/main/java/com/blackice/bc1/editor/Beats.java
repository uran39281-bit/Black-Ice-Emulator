package com.blackice.bc1.editor;

import java.util.*;

/** Approximate beat markers from decoded waveform peaks: local energy rises well above the running
 * average, at least a minimum spacing apart. Markers stay editable; this is a starting point. */
public final class Beats {
    private Beats() {}
    public static List<Long> detect(float[] peaks,long sourceDurationMs,long minSpacingMs) {
        List<Long> beats=new ArrayList<>();if(peaks==null||peaks.length<8||sourceDurationMs<=0)return beats;
        double mean=0;for(float p:peaks)mean+=p;mean/=peaks.length;double var=0;for(float p:peaks)var+=(p-mean)*(p-mean);double sd=Math.sqrt(var/peaks.length);
        long last=-minSpacingMs;
        for(int i=1;i<peaks.length-1;i++){
            double local=0;int from=Math.max(0,i-8),count=0;for(int j=from;j<i;j++){local+=peaks[j];count++;}local/=Math.max(1,count);
            boolean peak=peaks[i]>=peaks[i-1]&&peaks[i]>=peaks[i+1]&&peaks[i]>Math.max(mean+.5*sd,local*1.25);
            long t=Math.round((i+.5)*sourceDurationMs/(double)peaks.length);
            if(peak&&t-last>=minSpacingMs){beats.add(t);last=t;}
        }
        return beats;
    }
}
