package android.provider;
import android.content.ContentResolver;
public class Settings {
 public static class Secure {
  public static int circle=1;
  public static int getInt(ContentResolver r,String key,int fallback){return circle;}
  public static String getString(ContentResolver r,String key) { return "other.wallet/.Pay"; }
 }
}
