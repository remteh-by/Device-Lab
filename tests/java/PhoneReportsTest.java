import com.remteh.phonecheck.PhoneReportCodec;
import java.util.Map;
public final class PhoneReportsTest {
 private static int assertions=0;
 static final String GOOD="{\"schema\":1,\"kind\":\"phone-intake\",\"session_id\":\"synthetic-test\",\"application\":\"RemTeh PhoneCheck\",\"version\":\"0.2.0\",\"source\":\"operator/android-api\",\"device\":\"Synthetic device\",\"os\":\"Android 15\",\"timezone\":\"UTC\",\"started_at_ms\":1730000000000,\"duration_ms\":4000,\"checks\":{\"screen\":{\"status\":\"not-measured\"}},\"comment\":\"<script>not executable</script>\"}";
 static void refuses(String value){try{PhoneReportCodec.parse(value);throw new AssertionError("Accepted malformed input");}catch(IllegalArgumentException expected){assertions++;}}
 public static void main(String[] args){
  Map<String,Object> a=PhoneReportCodec.parse(GOOD);assert a.get("device").equals("Synthetic device");assertions++;
  assert PhoneReportCodec.comparable(a,PhoneReportCodec.parse(GOOD));assertions++;
  assert !PhoneReportCodec.comparable(a,PhoneReportCodec.parse(GOOD.replace("Android 15","Android 12")));assertions++;
  for(String input:new String[]{GOOD+"junk",GOOD.replace("schema\":1","schema\":2"),GOOD.replace("phone-intake","mouse"),GOOD.replace("not-measured","healthy"),GOOD.replace("1730000000000","-1"),GOOD.replace("4000","1e999"),GOOD.replace("schema\":1","schema\":1,\"schema\":1"),GOOD.replace("schema\":1","schema\":01"),GOOD.replace("checks\":{","checks\":["),"{",GOOD.substring(0,GOOD.length()-1)+",}",GOOD.replace("Synthetic device","\\u0000")})refuses(input);
  String deep="0";for(int i=0;i<25;i++)deep="["+deep+"]";refuses(GOOD.substring(0,GOOD.length()-1)+",\"extra\":"+deep+"}");
  StringBuilder big=new StringBuilder();for(int i=0;i<PhoneReportCodec.MAX_BYTES+1;i++)big.append(' ');refuses(big.toString());
  StringBuilder many=new StringBuilder("[");for(int i=0;i<10001;i++)many.append("0,");many.append("0]");refuses(GOOD.substring(0,GOOD.length()-1)+",\"extra\":"+many+"}");
  System.out.println("Phone report parser: "+assertions+" format, bounds, status and comparison checks passed");
 }
}
