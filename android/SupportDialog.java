package com.remteh.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.Button;
import android.view.ViewGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bundled destinations only; no network, payment SDK, reports or account data. */
public final class SupportDialog {
    private SupportDialog() {}

    public static void show(Activity host) {
        if (host.isFinishing() || host.isDestroyed()) return;
        try {
            JSONObject config;
            try (InputStream input=host.getAssets().open("remteh-support.json");
                 ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while ((count=input.read(buffer))!=-1) {
                    if (bytes.size()+count>131072) throw new IllegalArgumentException("Support data too large");
                    bytes.write(buffer,0,count);
                }
                config=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
            JSONObject texts=config.getJSONObject("texts");
            String language=host.getResources().getConfiguration().locale.getLanguage();
            JSONObject localized=texts.optJSONObject(language);
            final JSONObject copy=localized==null?texts.getJSONObject("en"):localized;
            JSONArray methods=config.getJSONArray("methods");
            String[] labels=new String[methods.length()];
            for (int i=0;i<labels.length;i++) {
                JSONObject method=methods.getJSONObject(i);
                labels[i]=method.getString("title")+"\n"+copy.getString("method_status_"+method.getString("status"));
            }
            new AlertDialog.Builder(host).setTitle(copy.getString("support_choose"))
                .setItems(labels,(dialog,index)->{
                    try { detail(host,methods.getJSONObject(index),copy); }
                    catch (Exception error) { unavailable(host); }
                }).setNegativeButton(android.R.string.cancel,null).show();
        } catch (Exception error) { unavailable(host); }
    }

    private static void detail(Activity host,JSONObject method,JSONObject copy) throws Exception {
        final String url=method.getString("url"),title=method.getString("title");
        Uri destination=Uri.parse(url);
        if (!"https".equals(destination.getScheme()) || destination.getHost()==null || destination.getUserInfo()!=null)
            throw new IllegalArgumentException("Invalid destination");
        final String fallback=copy.getString("support_copy_failed");
        TextView description=new TextView(host);
        int inset=(int)(20*host.getResources().getDisplayMetrics().density);
        description.setPadding(inset,inset,inset,inset); description.setTextSize(16);
        description.setTextIsSelectable(true);
        description.setText(copy.getString("support_note")+"\n\n"+
            copy.getString("method_cadence_"+method.getString("cadence"))+"\n"+
            copy.getString("method_status_"+method.getString("status"))+"\n\n"+url+"\n\n"+copy.getString("support_provider_note"));
        // Keep actions inside the scrollable body: long translations must remain reachable
        // in landscape instead of overflowing AlertDialog's fixed button panel.
        LinearLayout content=new LinearLayout(host);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(inset,0,inset,inset);description.setPadding(0,inset,0,inset);content.addView(description);
        Button open=new Button(host),copyLink=new Button(host),choose=new Button(host);
        for (Button action:new Button[]{open,copyLink,choose}) {
            action.setAllCaps(false);action.setMinHeight((int)(48*host.getResources().getDisplayMetrics().density));
            content.addView(action,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        open.setText(copy.getString("support_open_provider"));copyLink.setText(copy.getString("support_copy_link"));choose.setText(copy.getString("support_choose"));
        ScrollView scroll=new ScrollView(host);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(host).setTitle(title).setView(scroll)
            .setNegativeButton(android.R.string.cancel,null).create();
        open.setOnClickListener(view->{
            try { host.startActivity(new Intent(Intent.ACTION_VIEW,destination)); }
            catch (android.content.ActivityNotFoundException | SecurityException error) {
                Toast.makeText(host,fallback,Toast.LENGTH_LONG).show();
            }
        });
        copyLink.setOnClickListener(view->{
            try {
                ClipboardManager clipboard=(ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard==null) throw new IllegalStateException("Clipboard unavailable");
                clipboard.setPrimaryClip(ClipData.newPlainText(title,url));
                if (android.os.Build.VERSION.SDK_INT<33)
                    Toast.makeText(host,copy.optString("support_copied"),Toast.LENGTH_SHORT).show();
            } catch (RuntimeException error) { Toast.makeText(host,fallback,Toast.LENGTH_LONG).show(); }
        });
        choose.setOnClickListener(view->{dialog.dismiss();show(host);});
        dialog.show();
    }

    private static void unavailable(Activity host) {
        new AlertDialog.Builder(host).setTitle("RemTeh")
            .setMessage("Support information is unavailable. All diagnostic features remain available.")
            .setPositiveButton(android.R.string.ok,null).show();
    }
}
