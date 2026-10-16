package com.blackice.bc1.editor;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
/** Portable project plus media; restores as a new project and never overwrites existing assets. */
public final class ProjectBundle {
 private static final long MAX_BYTES=8L*1024*1024*1024;
 public static void write(Context context,EditProject original,OutputStream destination)throws Exception{
  EditProject p=ProjectStore.snapshot(original);Map<String,String> names=new LinkedHashMap<>();
  for(EditProject.Clip c:p.clips)c.uri=asset(names,c.uri);
  for(EditProject.Overlay o:p.overlays)if(o.imageUri!=null)o.imageUri=asset(names,o.imageUri);
  if(p.music!=null)p.music.uri=asset(names,p.music.uri);
  try(ZipOutputStream out=new ZipOutputStream(destination)){out.putNextEntry(new ZipEntry("project.json"));out.write(ProjectStore.encode(p).getBytes(StandardCharsets.UTF_8));out.closeEntry();
   byte[] buffer=new byte[128*1024];long total=0;
   for(Map.Entry<String,String> e:names.entrySet()){out.putNextEntry(new ZipEntry(e.getValue()));try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(e.getKey()))){if(in==null)throw new IOException("Source unavailable");int n;while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Bundle exceeds 8 GiB");out.write(buffer,0,n);}}out.closeEntry();}
  }
 }
 private static String asset(Map<String,String> names,String uri){if(!names.containsKey(uri))names.put(uri,"assets/"+String.format(Locale.ROOT,"%04d",names.size())+".media");return names.get(uri);}
 public static EditProject read(Context context,InputStream source)throws Exception{
  File dir=new File(context.getFilesDir(),"bundle-"+UUID.randomUUID());if(!dir.mkdir())throw new IOException("Cannot create restore folder");boolean success=false;
  try{Set<String> names=new HashSet<>();long total=0;int count=0;byte[] buffer=new byte[128*1024];
   try(ZipInputStream zip=new ZipInputStream(source)){ZipEntry e;while((e=zip.getNextEntry())!=null){String name=e.getName();if(e.isDirectory()||!(name.equals("project.json")||name.matches("assets/[0-9]{4}\\.media"))||!names.add(name)||++count>122)throw new IOException("Invalid or duplicate bundle entry");
    File f=new File(dir,name);f.getParentFile().mkdirs();long entry=0;
    try(OutputStream out=new FileOutputStream(f)){int n;while((n=zip.read(buffer))!=-1){total+=n;entry+=n;if(total>MAX_BYTES||(name.equals("project.json")&&entry>4*1024*1024)||dir.getUsableSpace()<64L*1024*1024)throw new IOException("Bundle too large or insufficient storage");out.write(buffer,0,n);}}
   }}
   File json=new File(dir,"project.json");if(!json.isFile())throw new IOException("Missing project.json");EditProject p=ProjectStore.decode(new String(Files.readAllBytes(json.toPath()),StandardCharsets.UTF_8));
   for(EditProject.Clip c:p.clips)c.uri=resolve(dir,c.uri,names);
   for(EditProject.Overlay o:p.overlays)if(o.imageUri!=null)o.imageUri=resolve(dir,o.imageUri,names);
   if(p.music!=null)p.music.uri=resolve(dir,p.music.uri,names);
   p.id=UUID.randomUUID().toString();p.name=p.name.length()>109?p.name.substring(0,109)+" (restored)":p.name+" (restored)";new ProjectStore(context).save(p);success=true;return p;
  }finally{if(!success)discard(dir);}
 }
 private static String resolve(File dir,String name,Set<String> names)throws IOException{if(!name.matches("assets/[0-9]{4}\\.media")||!names.contains(name))throw new IOException("Missing bundled media");return Uri.fromFile(new File(dir,name)).toString();}
 private static void discard(File f){File[] files=f.listFiles();if(files!=null)for(File child:files)discard(child);f.delete();}
}
