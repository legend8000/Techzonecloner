package com.techzone.cloner;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

private List<ApplicationInfo> installedApps = new ArrayList<>();
private List<String> appNames = new ArrayList<>();
private EditText cloneNumberInput;
private TextView statusText;

@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);

LinearLayout layout = new LinearLayout(this);
layout.setOrientation(LinearLayout.VERTICAL);
layout.setPadding(32, 32, 32, 32);

statusText = new TextView(this);
statusText.setText("TechZone Cloner - Ready");
statusText.setPadding(0, 0, 0, 16);
layout.addView(statusText);

cloneNumberInput = new EditText(this);
cloneNumberInput.setHint("Enter Clone Profile ID (1, 2, 3...)");
cloneNumberInput.setText("1");
layout.addView(cloneNumberInput);

ListView listView = new ListView(this);
layout.addView(listView);

setContentView(layout);

loadInstalledApps();

listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, appNames));
listView.setOnItemClickListener((p, v, pos, id) -> showActionDialog(installedApps.get(pos), getSelectedUserId()));
}

private int getSelectedUserId() {
try {
return Integer.parseInt(cloneNumberInput.getText().toString().trim());
} catch (Exception e) {
return 1;
}
}

private void loadInstalledApps() {
PackageManager pm = getPackageManager();
for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !app.packageName.equals(getPackageName())) {
installedApps.add(app);
appNames.add(pm.getApplicationLabel(app).toString() + " (" + app.packageName + ")");
}
}
}

private void showActionDialog(ApplicationInfo app, int userId) {
String[] options = {
"1. Create Isolated Clone + New Device ID",
"2. Generate New Random Device ID",
"3. Launch Isolated App Profile #" + userId,
"4. Wipe Clone Profile #" + userId
};

new AlertDialog.Builder(this)
.setTitle(app.packageName + " [Profile #" + userId + "]")
.setItems(options, (d, w) -> {
if (w == 0) createCloneEnvironment(app, userId);
else if (w == 1) applyNewDeviceIdentity(app.packageName, userId);
else if (w == 2) launchClonedProfile(app.packageName, userId);
else if (w == 3) wipeCloneProfile(app.packageName, userId);
}).show();
}

private void createCloneEnvironment(ApplicationInfo app, int userId) {
try {
File sandboxDir = new File(getFilesDir(), "sandbox_user_" + userId + "/" + app.packageName);
if (!sandboxDir.exists()) sandboxDir.mkdirs();

File sourceApk = new File(app.sourceDir);
File clonedApk = new File(sandboxDir, "base_clone.apk");
copyFile(sourceApk, clonedApk);

String newId = applyNewDeviceIdentity(app.packageName, userId);
statusText.setText("Profile #" + userId + " Active | Fake ID: " + newId);
Toast.makeText(this, "Clone Created! Fake Android ID: " + newId, Toast.LENGTH_LONG).show();
launchClonedProfile(app.packageName, userId);
} catch (Exception e) {
Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
}
}

private String applyNewDeviceIdentity(String pkg, int userId) {
String h = UUID.randomUUID().toString().replace("-", "");
String fakeAndroidId = h.substring(0, 16);
String fakeModel = "SM-S928B-" + h.substring(0, 4).toUpperCase();
String fakeSerial = h.substring(16, 28).toUpperCase();

SharedPreferences prefs = getSharedPreferences("clone_id_" + userId + "_" + pkg, Context.MODE_PRIVATE);
prefs.edit()
.putString("ANDROID_ID", fakeAndroidId)
.putString("MODEL", fakeModel)
.putString("SERIAL", fakeSerial)
.apply();

try {
setBuildField("MODEL", fakeModel);
setBuildField("SERIAL", fakeSerial);
} catch (Exception ignored) {}

Toast.makeText(this, "New Identity Set: " + fakeModel + " / ID: " + fakeAndroidId, Toast.LENGTH_SHORT).show();
return fakeAndroidId;
}

private void launchClonedProfile(String pkg, int userId) {
SharedPreferences prefs = getSharedPreferences("clone_id_" + userId + "_" + pkg, Context.MODE_PRIVATE);
String fakeId = prefs.getString("ANDROID_ID", "None");
if ("None".equals(fakeId)) {
fakeId = applyNewDeviceIdentity(pkg, userId);
}

Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
if (launchIntent != null) {
launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
launchIntent.putExtra("SPOOFED_ANDROID_ID", fakeId);
launchIntent.putExtra("CLONE_USER_ID", userId);
startActivity(launchIntent);
} else {
Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show();
}
}

private void wipeCloneProfile(String pkg, int userId) {
File sandboxDir = new File(getFilesDir(), "sandbox_user_" + userId + "/" + pkg);
deleteRecursive(sandboxDir);
getSharedPreferences("clone_id_" + userId + "_" + pkg, Context.MODE_PRIVATE).edit().clear().apply();
Toast.makeText(this, "Profile #" + userId + " Wiped!", Toast.LENGTH_SHORT).show();
}

private void copyFile(File src, File dst) throws Exception {
try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
byte[] buf = new byte[8192];
int len;
while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
}
}

private void deleteRecursive(File f) {
if (f.isDirectory()) for (File c : f.listFiles()) deleteRecursive(c);
f.delete();
}

private void setBuildField(String fieldName, String value) throws Exception {
Field field = Build.class.getDeclaredField(fieldName);
field.setAccessible(true);
field.set(null, value);
}
}
