package com.remteh.qa;
import android.app.*;import android.os.*;import android.content.*;import android.view.accessibility.AccessibilityNodeInfo;import android.graphics.Bitmap;import org.json.*;import java.util.*;import java.io.*;
/** Test-only SDK fixture: never launches a payment page or transfers money. */
public final class SupportQuality {
 public static void verify(Instrumentation test,Activity host)throws Exception{
  if(!Build.MODEL.toLowerCase(Locale.ROOT).contains("sdk"))throw new AssertionError("SDK emulator only");
  byte[] bytes;try(InputStream in=host.getAssets().open("remteh-support.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();}
  JSONObject data=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));JSONArray methods=data.getJSONArray("methods");if(methods.length()!=6)throw new AssertionError("Expected six destinations");
  JSONObject texts=data.getJSONObject("texts");String language=host.getResources().getConfiguration().locale.getLanguage();JSONObject copy=texts.optJSONObject(language);if(copy==null)copy=texts.getJSONObject("en");
  final List<String> opened=new ArrayList<>();Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_VIEW.equals(intent.getAction())){opened.add(intent.getDataString());return new Instrumentation.ActivityResult(Activity.RESULT_OK,null);}return null;}};test.addMonitor(monitor);
  try{
   test.runOnMainSync(()->com.remteh.support.SupportDialog.show(host));test.waitForIdleSync();
   for(int i=0;i<methods.length();i++){
    JSONObject method=methods.getJSONObject(i);if(method.getString("id").equals("patreon"))throw new AssertionError("Removed provider returned");
    click(test,method.getString("title"),true);String url=method.getString("url");if(!contains(test,url))throw new AssertionError("Destination missing: "+method.getString("id"));
    click(test,copy.getString("support_copy_link"),false);final String[] copied={null};test.runOnMainSync(()->{android.content.ClipboardManager cm=(android.content.ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);if(cm!=null&&cm.hasPrimaryClip())copied[0]=cm.getPrimaryClip().getItemAt(0).coerceToText(host).toString();});if(!url.equals(copied[0]))throw new AssertionError("Copied destination mismatch");
    click(test,copy.getString("support_open_provider"),false);if(opened.size()!=i+1||!url.equals(opened.get(i)))throw new AssertionError("Browser destination mismatch");
    if(method.getString("id").equals("donatepay")){Bitmap bmp=test.getUiAutomation().takeScreenshot();File dir=host.getExternalFilesDir("qa");if(!dir.exists())dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"support-method.png"))){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}bmp.recycle();}
    click(test,copy.getString("support_choose"),false);
   }
   if(contains(test,"Patreon"))throw new AssertionError("Removed provider shown");
   click(test,host.getString(android.R.string.cancel),false);
  }finally{test.removeMonitor(monitor);}
 }
 private static boolean contains(Instrumentation t,String value){AccessibilityNodeInfo root=t.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(value).isEmpty();}
 private static void click(Instrumentation t,String text,boolean starts)throws Exception{
  for(int attempt=0;attempt<40;attempt++){AccessibilityNodeInfo root=t.getUiAutomation().getRootInActiveWindow();if(root!=null){for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text)){if(!n.isVisibleToUser())continue;String value=n.getText()==null?"":n.getText().toString();if(starts?!value.startsWith(text):!value.equalsIgnoreCase(text))continue;while(n!=null&&!n.isClickable())n=n.getParent();if(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){t.waitForIdleSync();Thread.sleep(120);return;}}scroll(root);}Thread.sleep(100);}throw new AssertionError("Support control missing: "+text);
 }
 private static void scroll(AccessibilityNodeInfo node){if(node.isScrollable()){node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);return;}for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo n=node.getChild(i);if(n!=null)scroll(n);}}
}
