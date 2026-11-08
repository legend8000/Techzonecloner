package com.techzone.cloner;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.device.BDeviceConfig;
import top.niunaijun.blackbox.entity.pm.InstallResult;
public class MainActivity extends AppCompatActivity {
private List<ApplicationInfo> installedApps = new ArrayList<>();
private List<String> appNames = new ArrayList<>();
private EditText cloneNumberInput;
@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
LinearLayout layout = new LinearLayout(this);
layout.setOrientation(LinearLayout.VERTICAL);
layout.setPadding(32, 32, 32, 32);
cloneNumberInput = new EditText(this);
cloneNumberInput.setHint("Enter Clone ID (1, 2, 3)");
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
try { return Integer.parseInt(cloneNumberInput.getText().toString().trim()); } catch (Exception e) { return 1; }
}
private void loadInstalledApps() {
PackageManager pm = getPackageManager();
for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
if (((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) && !app.packageName.equals(getPackageName())) {
installedApps.add(app);
appNames.add(pm.getApplicationLabel(app).toString() + " (" + app.packageName + ")");
}
}
}
private void showActionDialog(ApplicationInfo app, int userId) {
String[] options = {"1. Clone + New Device ID", "2. Randomize ID", "3. Launch Clone", "4. Delete Clone"};
new AlertDialog.Builder(this).setTitle(app.packageName).setItems(options, (d, w) -> {
if (w == 0) cloneAndSpoofApp(app.packageName, userId);
else if (w == 1) { applyNewDeviceIdentity(userId); Toast.makeText(this, "New ID Applied", Toast.LENGTH_SHORT).show(); }
else if (w == 2) BlackBoxCore.get().launchApk(app.packageName, userId);
else if (w == 3) { BlackBoxCore.get().uninstallPackageAsUser(app.packageName, userId); Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); }
}).show();
}
private void cloneAndSpoofApp(String pkg, int userId) {
Toast.makeText(this, "Cloning...", Toast.LENGTH_SHORT).show();
new Thread(() -> {
InstallResult res = BlackBoxCore.get().installPackageAsUser(pkg, userId);
applyNewDeviceIdentity(userId);
runOnUiThread(() -> {
if (res.success) BlackBoxCore.get().launchApk(pkg, userId);
else Toast.makeText(this, "Error: " + res.msg, Toast.LENGTH_LONG).show();
});
}).start();
}
private void applyNewDeviceIdentity(int userId) {
BDeviceConfig c = new BDeviceConfig();
String h = UUID.randomUUID().toString().replace("-", "");
c.androidId = h.substring(0, 16);
c.serial = h.substring(16, 28).toUpperCase();
c.MODEL = "SM-S928B-" + h.substring(0, 4).toUpperCase();
c.BRAND = "samsung";
c.MANUFACTURER = "Samsung";
c.PRODUCT = "e3qxeea";
c.DEVICE = "e3q";
c.BOARD = "kalama";
BlackBoxCore.get().setDeviceConfig(userId, c);
}
}
