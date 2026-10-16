package com.blackice.bc1.editor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Small shareable failure summary: no exception messages, media paths or project contents. */
public final class ExportDiagnostics {
    private ExportDiagnostics() {}
    public static String describe(String stage,Throwable error,int code) {
        String safeStage="encoding".equals(stage)?"encoding":"saving".equals(stage)?"saving":"preparation";
        StringBuilder report=new StringBuilder("BC-1 export diagnostic v1\nStage: ").append(safeStage)
                .append("\nMedia3 error code: ").append(code==0?"n/a":Integer.toString(code)).append("\nCauses:");
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        int count=0;
        while(error!=null&&count<8&&seen.add(error)) {
            String type=error.getClass().getName();
            report.append("\n- ").append(type.substring(0,Math.min(160,type.length())));
            error=error.getCause();count++;
        }
        if(count==0)report.append(" none recorded");
        if(error!=null)report.append("\n[remaining causes omitted]");
        return report.toString();
    }
}
