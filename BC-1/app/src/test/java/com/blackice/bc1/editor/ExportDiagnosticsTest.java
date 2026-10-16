package com.blackice.bc1.editor;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportDiagnosticsTest {
    @Test public void includesStagesCodesAndCausesButNeverMessages() {
        String secret="content://private/customer/movie.mp4 token=secret";
        Throwable error=new IllegalStateException(secret,new IOException(secret));
        String report=ExportDiagnostics.describe("encoding",error,7001);
        assertTrue(report.contains("Stage: encoding"));assertTrue(report.contains("7001"));
        assertTrue(report.contains("java.lang.IllegalStateException"));assertTrue(report.contains("java.io.IOException"));
        assertFalse(report.contains(secret));assertFalse(report.contains("content://"));
    }
    @Test public void normalizesStageAndHandlesMissingCause() {
        String report=ExportDiagnostics.describe("/private/path",null,0);
        assertTrue(report.contains("Stage: preparation"));assertTrue(report.contains("n/a"));
        assertTrue(report.contains("none recorded"));assertFalse(report.contains("/private/path"));
        assertTrue(ExportDiagnostics.describe("saving",new IOException(),0).contains("Stage: saving"));
    }
    @Test public void boundsLongAndCyclicChains() {
        Throwable error=new IOException();for(int i=0;i<100;i++)error=new IOException(error);
        String report=ExportDiagnostics.describe("encoding",error,1);
        assertEquals(8,report.split("\n- ",-1).length-1);assertTrue(report.length()<1600);
        IOException first=new IOException(),second=new IOException(first);first.initCause(second);
        report=ExportDiagnostics.describe("encoding",first,1);
        assertEquals(2,report.split("\n- ",-1).length-1);assertTrue(report.contains("omitted"));
    }
}
