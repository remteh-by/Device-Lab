package com.remteh.phonecheck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Read-only imported reports; the current measurement session is never replaced. */
final class PhoneReportUi {
 interface Current {JSONObject report() throws Exception;}
 interface Export {void export(JSONObject report);}
 private final Activity host;private final Current current;private final Export exporter;
 private JSONObject before;
 PhoneReportUi(Activity owner,Current source,Export target){host=owner;current=source;exporter=target;}
 String saved(){return before==null?null:before.toString();}
 void restore(String raw){try{if(raw!=null){PhoneReportCodec.parse(raw);before=new JSONObject(raw);}}catch(Exception ignored){notice("Отчёт для сравнения не восстановлен. Текущая проверка сохранена.");}}
 boolean hasReport(){return before!=null;}
 void open(){try{host.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),40);}catch(Exception e){notice("Нет системного выбора файлов");}}
 void receive(Uri uri){
  try(InputStream input=host.getContentResolver().openInputStream(uri);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
   if(input==null)throw new java.io.IOException("Файл не прочитан");byte[] buffer=new byte[4096];int count;
   while((count=input.read(buffer))!=-1){if(bytes.size()+count>PhoneReportCodec.MAX_BYTES)throw new IllegalArgumentException("Файл больше 128 КБ");bytes.write(buffer,0,count);}
   String raw=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);PhoneReportCodec.parse(raw);JSONObject accepted=new JSONObject(raw);before=accepted;compare();
  }catch(Exception e){notice("Импорт отклонён: "+(e.getMessage()==null?"ошибка чтения":e.getMessage())+". Текущая проверка и прежнее сравнение сохранены.");}
 }
 static String name(String key){String[] keys={"screen","touch","speaker","microphone","vibration","sensors","camera","battery"},names={"Экран","Мультитач","Динамик","Микрофон","Вибрация","Датчики","Камера","Батарея"};for(int i=0;i<keys.length;i++)if(keys[i].equals(key))return names[i];return key;}
 static String state(String key){switch(key){case "observed":return "Наблюдалось в тесте";case "suspicion":return "Есть замечание";case "unavailable":return "Недоступно";default:return "Не измерено";}}
 static String readable(JSONObject report){
  StringBuilder out=new StringBuilder(report.optString("device")+" · "+report.optString("os")+"\n"+report.optString("application")+" "+report.optString("version")+"\nИсточник: "+report.optString("source")+"\nСессия: "+report.optString("session_id")+"\n");
  JSONObject checks=report.optJSONObject("checks");if(checks!=null){java.util.Iterator<String> keys=checks.keys();while(keys.hasNext()){String key=keys.next();JSONObject row=checks.optJSONObject(key);if(row!=null){out.append("\n").append(name(key)).append(": ").append(state(row.optString("status")));if(!row.optString("detail").isEmpty())out.append("\n").append(row.optString("detail"));}}}
  return out.append("\n\nКомментарий: ").append(report.optString("comment","")).append("\n\n").append(report.optString("limitations","Наблюдения не являются аппаратным диагнозом.")).toString();
 }
 void compare(){
  if(before==null){notice("Сначала откройте JSON phone-intake, до 128 КБ");return;}
  try{
   JSONObject after=current.report();boolean compatible=PhoneReportCodec.comparable(PhoneReportCodec.parse(before.toString()),PhoneReportCodec.parse(after.toString()));
   StringBuilder out=new StringBuilder(compatible?"Тип, источник, устройство и версия совпадают. Сравните наблюдения; автоматического вывода об исправности нет.":"Устройство, Android, источник или версия отличаются. Прямое сравнение ограничено.");
   out.append("\n\nДО: ").append(before.optString("device")).append(" · ").append(before.optString("version")).append("\nПОСЛЕ: ").append(after.optString("device")).append(" · ").append(after.optString("version")).append("\n");
   JSONObject a=before.getJSONObject("checks"),b=after.getJSONObject("checks");java.util.LinkedHashSet<String> keys=new java.util.LinkedHashSet<>();a.keys().forEachRemaining(keys::add);b.keys().forEachRemaining(keys::add);
   for(String key:keys){JSONObject left=a.optJSONObject(key),right=b.optJSONObject(key);out.append("\n").append(name(key)).append("\n").append(state(left==null?"not-measured":left.optString("status"))).append(" → ").append(state(right==null?"not-measured":right.optString("status")));if(left!=null&&!left.optString("detail").isEmpty())out.append("\nДо: ").append(left.optString("detail"));if(right!=null&&!right.optString("detail").isEmpty())out.append("\nПосле: ").append(right.optString("detail"));}
   out.append("\n\nКомментарий до: ").append(before.optString("comment","")).append("\nПосле: ").append(after.optString("comment",""));
   new AlertDialog.Builder(host).setTitle("Сравнение до / после").setMessage(out).setPositiveButton("Экспорт текущей",(d,w)->exporter.export(after)).setNegativeButton("Закрыть",null).show();
  }catch(Exception e){notice("Сравнение недоступно для этого формата. Текущая проверка сохранена.");}
 }
 void support(){new AlertDialog.Builder(host).setTitle("Поддержка RemTeh").setMessage("Откроется Ko-fi в браузере. Разовый донат добровольный; сумму выбираете вы. Отчёты, фото и звук не передаются. Оплату обрабатывает Ko-fi.").setPositiveButton("Открыть Ko-fi",(d,w)->web("https://ko-fi.com/pavelzayats")).setNegativeButton("Позже",null).show();}
 void web(String url){try{host.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){notice("Не найден браузер для открытия ссылки");}}
 private void notice(String message){Toast.makeText(host,message,Toast.LENGTH_LONG).show();}
}
