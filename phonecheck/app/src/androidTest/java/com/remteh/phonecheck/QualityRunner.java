package com.remteh.phonecheck;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;
import android.graphics.Bitmap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;

/** Disposable SDK emulator only. Not included in the distributed application APK. */
public final class QualityRunner extends Instrumentation {
 private Bundle arguments;private volatile Activity current;private int checks;
 @Override public void onCreate(Bundle args){arguments=args;start();}
 @Override public void callActivityOnCreate(Activity activity,Bundle state){current=activity;super.callActivityOnCreate(activity,state);}
 private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
 @Override public void onStart(){Bundle result=new Bundle();try{
  check(android.os.Build.MODEL.toLowerCase(java.util.Locale.ROOT).contains("sdk"),"Disposable SDK emulator required");
  current=startActivitySync(new Intent().setClassName(getTargetContext(),"com.remteh.phonecheck.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  if("baseline".equals(arguments.getString("phase"))){
   click("Комментарий мастера");setText("Synthetic update retention QA");click("Сохранить");click("Результаты и экспорт JSON");click("Закрыть");
   JSONArray rows=new JSONArray(preferences().getString("history","[]"));check(rows.length()>0&&rows.getJSONObject(0).optString("comment").equals("Synthetic update retention QA"),"Baseline history saved through real UI");
  }else{
   JSONArray rows=new JSONArray(preferences().getString("history","[]"));boolean retained=false;for(int i=0;i<rows.length();i++)retained|=rows.getJSONObject(i).optString("comment").equals("Synthetic update retention QA");check(retained,"APK update retained existing history");
   String[] permissions=getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),PackageManager.GET_PERMISSIONS).requestedPermissions;
   if(permissions!=null)for(String p:permissions)check(!p.equals("android.permission.INTERNET")&&!p.equals("android.permission.READ_CONTACTS")&&!p.equals("android.permission.READ_SMS"),"No network or customer-data permission");
   click("Комментарий мастера");setText("Synthetic current session");click("Сохранить");click("Результаты и экспорт JSON");click("Закрыть");JSONObject draft=new JSONObject(preferences().getString("draft","{}"));String id=draft.getString("session_id");
   click("Мультитач");click("Закончить тест");JSONObject touch=new JSONObject(preferences().getString("draft","{}"));check(touch.getJSONObject("checks").getJSONObject("touch").getString("status").equals("not-measured"),"No contacts is not a successful touch test");
   call("onRequestPermissionsResult",new Class<?>[]{int.class,String[].class,int[].class},new Object[]{10,new String[]{"android.permission.RECORD_AUDIO"},new int[]{PackageManager.PERMISSION_DENIED}});
   JSONObject denied=new JSONObject(preferences().getString("draft","{}"));check(denied.getJSONObject("checks").getJSONObject("microphone").getString("status").equals("unavailable"),"Denied microphone remains unavailable");
   JSONObject imported=new JSONObject(draft.toString());imported.put("device","Synthetic imported device");imported.put("comment","<script>inert imported text</script>");File valid=fixture("valid.json",imported.toString());receive(valid);check(contains("Сравнение до / после"),"Comparison displayed for validated import");click("Закрыть");String before=importState();check(before.contains("Synthetic imported device"),"Imported source stays separate");check(new JSONObject(preferences().getString("draft","{}")).getString("session_id").equals(id),"Import preserves current session identity");
   receive(fixture("bad.json","{\"schema\":1,\"kind\":\"phone-intake\",\"checks\":{\"screen\":{\"status\":\"healthy\"}}}"));check(importState().equals(before),"Invalid import preserves prior comparison");
   Activity original=current;runOnMainSync(()->current.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));for(int i=0;i<80&&current==original;i++)Thread.sleep(100);waitForIdleSync();check(current!=original,"Landscape recreation occurred");check(importState().equals(before),"Rotation preserves imported source");
   runOnMainSync(()->current.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(700);waitForIdleSync();
   click("Поддержать разработку ↗");check(contains("Выберите способ поддержки")||contains("Choose a support method"),"Provider chooser opened");click(current.getString(android.R.string.cancel));
   File directory=new File(getTargetContext().getApplicationInfo().dataDir,"shared_prefs");check(directory.setWritable(false,false),"Prepare isolated write failure");
   try{click("Новая проверка");click("Сохранить и начать");Field f=current.getClass().getDeclaredField("session");f.setAccessible(true);check(f.get(current).equals(id),"Failed storage does not clear current session");}finally{check(directory.setWritable(true,true),"Restore test directory permissions");}
   screenshot("phonecheck-0.2.1.png");com.remteh.qa.SupportQuality.verify(this,current);result.putString("support_methods","6");
  }
  runOnMainSync(()->current.finish());waitForIdleSync();result.putString("passed","true");result.putInt("checks",checks);result.putString("scope","SDK Android 15, synthetic data; no hardware metrology or payment");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){try{screenshot("failure.png");}catch(Exception ignored){}result.putString("passed","false");result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}}
 private android.content.SharedPreferences preferences(){return getTargetContext().getSharedPreferences("MainActivity",0);}
 private void call(String name,Class<?>[] types,Object[] args)throws Exception{Method m=current.getClass().getDeclaredMethod(name,types);m.setAccessible(true);final Throwable[] fail={null};runOnMainSync(()->{try{m.invoke(current,args);}catch(Throwable e){fail[0]=e;}});if(fail[0]!=null)throw new RuntimeException(fail[0]);waitForIdleSync();}
 private File fixture(String name,String value)throws Exception{File f=new File(getTargetContext().getCacheDir(),name);try(FileOutputStream out=new FileOutputStream(f)){out.write(value.getBytes(StandardCharsets.UTF_8));}return f;}
 private void receive(File f)throws Exception{call("onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},new Object[]{40,Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(f))});}
 private String importState()throws Exception{Field field=current.getClass().getDeclaredField("reports");field.setAccessible(true);Object reportUi=field.get(current);Method m=reportUi.getClass().getDeclaredMethod("saved");m.setAccessible(true);return (String)m.invoke(reportUi);}
 private boolean contains(String text){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return false;return !root.findAccessibilityNodeInfosByText(text).isEmpty();}
 private void click(String text)throws Exception{
  for(int attempt=0;attempt<60;attempt++){
   AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null){List<AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByText(text);for(AccessibilityNodeInfo item:matches){if(item.getText()!=null&&!item.getText().toString().equalsIgnoreCase(text))continue;AccessibilityNodeInfo button=item;while(button!=null&&!button.isClickable())button=button.getParent();if(button!=null&&button.performAction(AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();Thread.sleep(100);return;}}
    scroll(root);
   }Thread.sleep(100);
  }throw new AssertionError("UI control missing: "+text);
 }
 private void scroll(AccessibilityNodeInfo root){if(root.isScrollable()){root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);return;}for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo child=root.getChild(i);if(child!=null)scroll(child);}}
 private void setText(String value)throws Exception{for(int attempt=0;attempt<50;attempt++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow(),field=findEdit(root);if(field!=null){Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);if(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){waitForIdleSync();return;}}Thread.sleep(100);}throw new AssertionError("Editable comment missing");}
 private AccessibilityNodeInfo findEdit(AccessibilityNodeInfo node){if(node==null)return null;if("android.widget.EditText".contentEquals(node.getClassName()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo result=findEdit(node.getChild(i));if(result!=null)return result;}return null;}
 private void top(View view){if(view instanceof ScrollView)((ScrollView)view).fullScroll(View.FOCUS_UP);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)top(((ViewGroup)view).getChildAt(i));}
 private void screenshot(String name)throws Exception{runOnMainSync(()->top(current.getWindow().getDecorView()));waitForIdleSync();Thread.sleep(200);Bitmap bitmap=getUiAutomation().takeScreenshot();File folder=getTargetContext().getExternalFilesDir("qa");if(folder==null||!folder.exists()&&!folder.mkdirs())throw new java.io.IOException("Screenshot directory");try(FileOutputStream out=new FileOutputStream(new File(folder,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
