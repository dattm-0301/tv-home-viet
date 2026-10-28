package com.tvhomeviet.demo;
public class MainActivity extends android.app.Activity {
 @Override public void onCreate(android.os.Bundle b) {
  super.onCreate(b);
  android.widget.TextView v=new android.widget.TextView(this);
  if (getIntent().getBooleanExtra("security_probe", false)) {
   boolean themeDenied=false, clockDenied=false;
   try (android.os.ParcelFileDescriptor f=getContentResolver().openFileDescriptor(android.net.Uri.parse("content://com.tvhomeviet.launcher.theme/config.json"),"r")) {
   } catch (SecurityException expected) { themeDenied=true; }
   catch (java.io.IOException ignored) { }
   try { startActivity(new android.content.Intent().setClassName("com.tvhomeviet.launcher","com.gathin.tvhome.ClockActivity")); }
   catch (SecurityException | android.content.ActivityNotFoundException expected) { clockDenied=true; }
   android.util.Log.i("TVHOME_SECURITY_PROBE","theme_denied="+themeDenied+" clock_denied="+clockDenied);
   v.setText("Security probe: theme denied="+themeDenied+", clock denied="+clockDenied);
  } else v.setText(getString(getApplicationInfo().labelRes)+"\nỨng dụng demo · không có dịch vụ phát nội dung");
  v.setTextSize(28);v.setTextColor(0xFFFFFFFF);v.setGravity(android.view.Gravity.CENTER);v.setBackgroundColor(0xFF15232F);setContentView(v);
 }
}
