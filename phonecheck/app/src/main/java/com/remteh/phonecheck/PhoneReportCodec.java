package com.remteh.phonecheck;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, bounded JSON import. This class has no Android or network dependency. */
public final class PhoneReportCodec {
 public static final int MAX_BYTES=131072;
 private PhoneReportCodec() {}
 @SuppressWarnings("unchecked")
 public static Map<String,Object> parse(String text) {
  if(text==null||text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw bad("Файл больше 128 КБ");
  Object value=new Parser(text).document();
  if(!(value instanceof Map))throw bad("Нужен объект отчёта");
  Map<String,Object> report=(Map<String,Object>)value;
  if(!Long.valueOf(1).equals(report.get("schema"))||!"phone-intake".equals(report.get("kind")))throw bad("Нужен phone-intake schema 1");
  for(String key:new String[]{"session_id","application","version","source","device","os","timezone"})string(report.get(key),key,400,false);
  count(report.get("started_at_ms"),"Время начала");count(report.get("duration_ms"),"Продолжительность");
  if(report.containsKey("comment"))string(report.get("comment"),"Комментарий",4000,true);
  if(report.containsKey("limitations"))string(report.get("limitations"),"Ограничения",4000,true);
  if(!(report.get("checks") instanceof Map))throw bad("Нет проверок телефона");
  Map<String,Object> checks=(Map<String,Object>)report.get("checks");if(checks.isEmpty()||checks.size()>32)throw bad("Неверное число проверок");
  for(Map.Entry<String,Object> entry:checks.entrySet()){
   string(entry.getKey(),"Название проверки",80,false);if(!(entry.getValue() instanceof Map))throw bad("Повреждена проверка");
   Map<String,Object> row=(Map<String,Object>)entry.getValue();Object status=row.get("status");
   if(!"observed".equals(status)&&!"suspicion".equals(status)&&!"not-measured".equals(status)&&!"unavailable".equals(status))throw bad("Неизвестный статус проверки");
   if(row.containsKey("detail"))string(row.get("detail"),"Наблюдение",4000,true);
   if(row.containsKey("source"))string(row.get("source"),"Источник",400,false);
   if(row.containsKey("observed_at_ms"))count(row.get("observed_at_ms"),"Время наблюдения");
  }
  return report;
 }
 public static boolean comparable(Map<String,Object> before,Map<String,Object> after){
  for(String key:new String[]{"kind","source","device","os","application","version"})if(!before.get(key).equals(after.get(key)))return false;
  return true;
 }
 private static void string(Object value,String label,int limit,boolean multiline){
  if(!(value instanceof String)||((String)value).length()>limit||(!multiline&&((String)value).trim().isEmpty()))throw bad(label+": неверный текст");
  for(char c:((String)value).toCharArray())if(c<32&&!(multiline&&(c=='\n'||c=='\r'||c=='\t')))throw bad(label+": управляющий символ");
 }
 private static void count(Object value,String label){
  if(!(value instanceof Number))throw bad(label+": неверное число");double n=((Number)value).doubleValue();if(!Double.isFinite(n)||n<0||n>9007199254740991d||n!=Math.floor(n))throw bad(label+": неверный счётчик");
 }
 private static IllegalArgumentException bad(String text){return new IllegalArgumentException(text);}
 private static final class Parser {
  final String text;int at,nodes;
  Parser(String value){text=value;}
  Object document(){Object value=value(0);space();if(at!=text.length())throw bad("Лишние данные после JSON");return value;}
  void space(){while(at<text.length()&&" \t\r\n".indexOf(text.charAt(at))>=0)at++;}
  Object value(int depth){
   space();if(depth>16||++nodes>10000)throw bad("Слишком сложный JSON");if(at==text.length())throw bad("Оборванный JSON");char c=text.charAt(at);
   if(c=='"')return string();
   if(c=='{'){
    at++;Map<String,Object> map=new LinkedHashMap<>();space();if(take('}'))return map;
    do{space();if(at==text.length()||text.charAt(at)!='"')throw bad("Ожидается имя поля");String key=string();space();need(':');if(map.containsKey(key))throw bad("Повторяющееся поле JSON");map.put(key,value(depth+1));space();if(take('}'))return map;need(',');}while(true);
   }
   if(c=='['){at++;List<Object> list=new ArrayList<>();space();if(take(']'))return list;do{list.add(value(depth+1));space();if(take(']'))return list;need(',');}while(true);}
   for(String literal:new String[]{"true","false","null"})if(text.startsWith(literal,at)){at+=literal.length();return literal.equals("null")?null:literal.equals("true");}
   int start=at;if(c=='-')at++;if(at==text.length())throw bad("Оборванное число");
   if(take('0')){if(at<text.length()&&Character.isDigit(text.charAt(at)))throw bad("Начальный ноль в числе");}else{if(at==text.length()||text.charAt(at)<'1'||text.charAt(at)>'9')throw bad("Некорректное значение JSON");while(at<text.length()&&digit())at++;}
   boolean integer=true;
   if(take('.')){integer=false;int from=at;while(at<text.length()&&digit())at++;if(from==at)throw bad("Неполная дробь");}
   if(at<text.length()&&(text.charAt(at)=='e'||text.charAt(at)=='E')){integer=false;at++;if(at<text.length()&&(text.charAt(at)=='+'||text.charAt(at)=='-'))at++;int from=at;while(at<text.length()&&digit())at++;if(from==at)throw bad("Неполная степень");}
   String number=text.substring(start,at);if(number.length()>32)throw bad("Слишком длинное число");
   try{if(integer)return Long.valueOf(number);double result=Double.parseDouble(number);if(!Double.isFinite(result))throw bad("Нечисловое значение");return result;}catch(NumberFormatException error){throw bad("Число вне диапазона");}
  }
  boolean digit(){return text.charAt(at)>='0'&&text.charAt(at)<='9';}
  boolean take(char c){if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
  void need(char c){space();if(!take(c))throw bad("Нарушена структура JSON");}
  String string(){
   need('"');StringBuilder result=new StringBuilder();
   while(at<text.length()){
    char c=text.charAt(at++);if(c=='"')return result.toString();if(c<32)throw bad("Управляющий символ в JSON");
    if(c=='\\'){
     if(at==text.length())throw bad("Оборванная строка");c=text.charAt(at++);
     if(c=='u'){if(at+4>text.length())throw bad("Оборванный Unicode");try{c=(char)Integer.parseInt(text.substring(at,at+4),16);}catch(NumberFormatException e){throw bad("Некорректный Unicode");}at+=4;}
     else if(c=='n')c='\n';else if(c=='r')c='\r';else if(c=='t')c='\t';else if(c=='b')c='\b';else if(c=='f')c='\f';else if(c!='"'&&c!='\\'&&c!='/')throw bad("Неизвестное экранирование");
    }
    result.append(c);if(result.length()>4000)throw bad("Слишком длинная строка");
   }
   throw bad("Оборванная строка JSON");
  }
 }
}
