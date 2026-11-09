package com.techzone.cloner;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.android.apksig.ApkSigner;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

private static class AppItem {
ApplicationInfo info;
String label;
String packageName;
Drawable icon;
boolean isSplitApk;
}

private static class CountingOutputStream extends FilterOutputStream {
long count = 0;

public CountingOutputStream(OutputStream out) {
super(out);
}

@Override
public void write(int b) throws IOException {
out.write(b);
count++;
}

@Override
public void write(byte[] b, int off, int len) throws IOException {
out.write(b, off, len);
count += len;
}
}

private List<AppItem> allApps = new ArrayList<>();
private List<AppItem> filteredApps = new ArrayList<>();
private AppListAdapter adapter;
private int currentCloneNum = 1;
private TextView profileBadge;
private TextView statusBanner;

@Override
protected void onCreate(Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
Security.removeProvider("BC");
Security.addProvider(new BouncyCastleProvider());

LinearLayout root = new LinearLayout(this);
root.setOrientation(LinearLayout.VERTICAL);
root.setBackgroundColor(Color.parseColor("#0F172A"));
root.setPadding(28, 36, 28, 16);

LinearLayout header = new LinearLayout(this);
header.setOrientation(LinearLayout.VERTICAL);
header.setBackground(createRoundedBg("#1E293B", 28, "#38BDF8", 2));
header.setPadding(32, 28, 32, 28);

TextView title = new TextView(this);
title.setText("⚡ TechZone Pro APK Cloner v4.2");
title.setTextColor(Color.parseColor("#38BDF8"));
title.setTextSize(20);
title.setTypeface(null, Typeface.BOLD);
header.addView(title);

statusBanner = new TextView(this);
statusBanner.setText("Android 13 ART Verifier Safe + v3.6 Core + 16KB Split Align");
statusBanner.setTextColor(Color.parseColor("#94A3B8"));
statusBanner.setTextSize(12);
statusBanner.setPadding(0, 8, 0, 20);
header.addView(statusBanner);

LinearLayout profileRow = new LinearLayout(this);
profileRow.setOrientation(LinearLayout.HORIZONTAL);
profileRow.setGravity(Gravity.CENTER_VERTICAL);

TextView profLabel = new TextView(this);
profLabel.setText("Clone Identity Profile:");
profLabel.setTextColor(Color.WHITE);
profLabel.setTextSize(14);
profLabel.setTypeface(null, Typeface.BOLD);
profLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
profileRow.addView(profLabel);

Button minusBtn = createSmallButton("-", "#334155");
profileBadge = new TextView(this);
profileBadge.setText("Clone #1");
profileBadge.setTextColor(Color.parseColor("#10B981"));
profileBadge.setTextSize(15);
profileBadge.setTypeface(null, Typeface.BOLD);
profileBadge.setPadding(28, 10, 28, 10);

Button plusBtn = createSmallButton("+", "#0284C7");

minusBtn.setOnClickListener(v -> {
if (currentCloneNum > 1) {
currentCloneNum--;
updateProfileUI();
}
});
plusBtn.setOnClickListener(v -> {
if (currentCloneNum < 9) {
currentCloneNum++;
updateProfileUI();
}
});

profileRow.addView(minusBtn);
profileRow.addView(profileBadge);
profileRow.addView(plusBtn);
header.addView(profileRow);
root.addView(header);

EditText searchBox = new EditText(this);
searchBox.setHint("🔍 Search apps to clone...");
searchBox.setHintTextColor(Color.parseColor("#64748B"));
searchBox.setTextColor(Color.WHITE);
searchBox.setTextSize(14);
searchBox.setBackground(createRoundedBg("#1E293B", 22, "#334155", 2));
searchBox.setPadding(32, 22, 32, 22);
LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
searchLp.setMargins(0, 24, 0, 20);
searchBox.setLayoutParams(searchLp);
root.addView(searchBox);

ListView listView = new ListView(this);
listView.setDivider(null);
listView.setDividerHeight(18);
root.addView(listView);

setContentView(root);

loadInstalledApps();
adapter = new AppListAdapter();
listView.setAdapter(adapter);

listView.setOnItemClickListener((p, v, pos, id) -> showControlPanel(filteredApps.get(pos)));

searchBox.addTextChangedListener(new TextWatcher() {
@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
@Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
@Override public void afterTextChanged(Editable s) {}
});

handleInstallResultIntent(getIntent());
}

private void updateProfileUI() {
profileBadge.setText("Clone #" + currentCloneNum);
adapter.notifyDataSetChanged();
}

private Button createSmallButton(String text, String bgHex) {
Button b = new Button(this);
b.setText(text);
b.setTextColor(Color.WHITE);
b.setTextSize(16);
b.setTypeface(null, Typeface.BOLD);
b.setBackground(createRoundedBg(bgHex, 16, null, 0));
b.setLayoutParams(new LinearLayout.LayoutParams(105, 95));
return b;
}

private GradientDrawable createRoundedBg(String fillHex, int radius, String strokeHex, int strokeWidth) {
GradientDrawable gd = new GradientDrawable();
gd.setColor(Color.parseColor(fillHex));
gd.setCornerRadius(radius);
if (strokeHex != null && strokeWidth > 0) gd.setStroke(strokeWidth, Color.parseColor(strokeHex));
return gd;
}

private void loadInstalledApps() {
PackageManager pm = getPackageManager();
allApps.clear();
for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !app.packageName.equals(getPackageName())) {
AppItem item = new AppItem();
item.info = app;
item.label = pm.getApplicationLabel(app).toString();
item.packageName = app.packageName;
item.icon = pm.getApplicationIcon(app);
item.isSplitApk = (app.splitSourceDirs != null && app.splitSourceDirs.length > 0);
allApps.add(item);
}
}
Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
filteredApps.addAll(allApps);
}

private void filterApps(String query) {
filteredApps.clear();
String q = query.toLowerCase().trim();
for (AppItem item : allApps) {
if (item.label.toLowerCase().contains(q) || item.packageName.toLowerCase().contains(q)) {
filteredApps.add(item);
}
}
adapter.notifyDataSetChanged();
}

private String generateClonedPackageName(String origPkg, int cloneNum) {
int len = origPkg.length();
char lastChar = origPkg.charAt(len - 1);
char newChar = (char) ('a' + (cloneNum % 25));
if (newChar == lastChar) newChar = 'z';
return origPkg.substring(0, len - 1) + newChar;
}

private String generateSafeDefaultLabel(String origLabel, int cloneNum) {
if (origLabel == null || origLabel.isEmpty()) return "Clone " + cloneNum;
if (origLabel.startsWith("ال") && origLabel.length() >= 4) {
return origLabel.substring(2) + " " + cloneNum;
}
if (origLabel.length() > 2) {
return origLabel.substring(0, origLabel.length() - 2) + " " + cloneNum;
}
return origLabel + cloneNum;
}

private boolean isPackageInstalled(String pkgName) {
try {
getPackageManager().getPackageInfo(pkgName, 0);
return true;
} catch (Exception e) {
return false;
}
}

private class AppListAdapter extends BaseAdapter {
@Override public int getCount() { return filteredApps.size(); }
@Override public Object getItem(int position) { return filteredApps.get(position); }
@Override public long getItemId(int position) { return position; }

@Override
public View getView(int position, View convertView, ViewGroup parent) {
AppItem item = filteredApps.get(position);
String targetClonePkg = generateClonedPackageName(item.packageName, currentCloneNum);
boolean installed = isPackageInstalled(targetClonePkg);

LinearLayout card = new LinearLayout(MainActivity.this);
card.setOrientation(LinearLayout.HORIZONTAL);
card.setGravity(Gravity.CENTER_VERTICAL);
card.setPadding(24, 24, 24, 24);
card.setBackground(createRoundedBg("#1E293B", 24, installed ? "#10B981" : "#334155", 2));

ImageView iconView = new ImageView(MainActivity.this);
iconView.setImageDrawable(item.icon);
LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(120, 120);
iconLp.setMargins(0, 0, 28, 0);
iconView.setLayoutParams(iconLp);
card.addView(iconView);

LinearLayout textCol = new LinearLayout(MainActivity.this);
textCol.setOrientation(LinearLayout.VERTICAL);
textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

TextView nameView = new TextView(MainActivity.this);
nameView.setText(item.label + " -> " + generateSafeDefaultLabel(item.label, currentCloneNum));
nameView.setTextColor(Color.WHITE);
nameView.setTextSize(15);
nameView.setTypeface(null, Typeface.BOLD);
textCol.addView(nameView);

TextView pkgView = new TextView(MainActivity.this);
pkgView.setText("Orig: " + item.packageName + "\nClone: " + targetClonePkg);
pkgView.setTextColor(Color.parseColor("#94A3B8"));
pkgView.setTextSize(11);
textCol.addView(pkgView);

TextView badge = new TextView(MainActivity.this);
if (installed) {
badge.setText("✔ Clone #" + currentCloneNum + " Installed (Android 13 Safe)");
badge.setTextColor(Color.parseColor("#10B981"));
} else {
badge.setText(item.isSplitApk ? "🛡️ Split Bundle (Android 13 DEX Verifier Safe)" : "🛡️ Standalone (Android 13 DEX Verifier Safe)");
badge.setTextColor(Color.parseColor(item.isSplitApk ? "#F59E0B" : "#38BDF8"));
}
badge.setTextSize(11);
badge.setPadding(0, 6, 0, 0);
textCol.addView(badge);

card.addView(textCol);
return card;
}
}

private void showControlPanel(AppItem item) {
String targetClonePkg = generateClonedPackageName(item.packageName, currentCloneNum);
boolean installed = isPackageInstalled(targetClonePkg);

LinearLayout panel = new LinearLayout(this);
panel.setOrientation(LinearLayout.VERTICAL);
panel.setBackgroundColor(Color.parseColor("#0F172A"));
panel.setPadding(40, 40, 40, 40);

LinearLayout topRow = new LinearLayout(this);
topRow.setOrientation(LinearLayout.HORIZONTAL);
topRow.setGravity(Gravity.CENTER_VERTICAL);

ImageView icon = new ImageView(this);
icon.setImageDrawable(item.icon);
LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(110, 110);
iconLp.setMargins(0, 0, 24, 0);
icon.setLayoutParams(iconLp);
topRow.addView(icon);

TextView title = new TextView(this);
title.setText(item.label + " (Clone #" + currentCloneNum + ")");
title.setTextColor(Color.WHITE);
title.setTextSize(17);
title.setTypeface(null, Typeface.BOLD);
topRow.addView(title);
panel.addView(topRow);

TextView nameLabel = new TextView(this);
nameLabel.setText("✏️ Clone App Name (Max " + item.label.length() + " chars for safe ARSC):");
nameLabel.setTextColor(Color.parseColor("#10B981"));
nameLabel.setTextSize(13);
nameLabel.setTypeface(null, Typeface.BOLD);
nameLabel.setPadding(0, 24, 0, 10);
panel.addView(nameLabel);

EditText customNameInput = new EditText(this);
customNameInput.setText(generateSafeDefaultLabel(item.label, currentCloneNum));
customNameInput.setTextColor(Color.WHITE);
customNameInput.setTextSize(15);
customNameInput.setBackground(createRoundedBg("#1E293B", 18, "#38BDF8", 2));
customNameInput.setPadding(28, 20, 28, 20);
panel.addView(customNameInput);

int splitCount = (item.info.splitSourceDirs != null) ? item.info.splitSourceDirs.length : 0;

TextView infoBox = new TextView(this);
infoBox.setText("📦 Package: " + targetClonePkg +
"\n🛡️ Engine: v3.6 Non-Crash Core + Android 13 DEX Sort Verifier" +
"\n🆔 Identity: Sort-Checked Android_ID & Unique Signing Key" +
"\n🧩 Mode: Multi-APK Split Session (" + (splitCount + 1) + " APKs)");
infoBox.setTextColor(Color.parseColor("#38BDF8"));
infoBox.setTextSize(11);
infoBox.setBackground(createRoundedBg("#1E293B", 18, "#334155", 2));
infoBox.setPadding(28, 24, 28, 24);
LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
boxLp.setMargins(0, 24, 0, 24);
infoBox.setLayoutParams(boxLp);
panel.addView(infoBox);

AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();

Button btnBuild = createActionButton("🔨 1. Build & Install Clone (Android 13 Safe)", "#10B981");
btnBuild.setOnClickListener(v -> {
String chosenName = customNameInput.getText().toString().trim();
if (chosenName.isEmpty()) chosenName = generateSafeDefaultLabel(item.label, currentCloneNum);
dialog.dismiss();
checkInstallPermissionAndBuild(item, targetClonePkg, chosenName);
});
panel.addView(btnBuild);

if (installed) {
Button btnOpen = createActionButton("🚀 2. Open Installed Clone #" + currentCloneNum, "#0284C7");
btnOpen.setOnClickListener(v -> {
dialog.dismiss();
Intent launch = getPackageManager().getLaunchIntentForPackage(targetClonePkg);
if (launch != null) startActivity(launch);
});
panel.addView(btnOpen);

Button btnUninstall = createActionButton("🗑️ 3. Uninstall Clone #" + currentCloneNum, "#EF4444");
btnUninstall.setOnClickListener(v -> {
dialog.dismiss();
Intent uninstall = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + targetClonePkg));
startActivity(uninstall);
});
panel.addView(btnUninstall);
}

dialog.show();
}

private void checkInstallPermissionAndBuild(AppItem item, String targetClonePkg, String customAppName) {
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
if (!getPackageManager().canRequestPackageInstalls()) {
new AlertDialog.Builder(this)
.setTitle("⚠️ Permission Needed")
.setMessage("Please enable 'Allow from this source' so TechZone Cloner can install Split APKs directly.")
.setPositiveButton("Enable", (d, w) -> {
Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
Uri.parse("package:" + getPackageName()));
startActivity(intent);
})
.setNegativeButton("Cancel", null)
.show();
return;
}
}
buildAndInstallCloneBundle(item, targetClonePkg, customAppName, currentCloneNum);
}

private Button createActionButton(String text, String colorHex) {
Button b = new Button(this);
b.setText(text);
b.setTextColor(Color.WHITE);
b.setTextSize(14);
b.setAllCaps(false);
b.setTypeface(null, Typeface.BOLD);
b.setBackground(createRoundedBg(colorHex, 20, null, 0));
LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
lp.setMargins(0, 0, 0, 18);
b.setLayoutParams(lp);
b.setPadding(24, 24, 24, 24);
return b;
}

private void buildAndInstallCloneBundle(AppItem item, String newPkg, String customAppName, int cloneNum) {
LinearLayout progressLayout = new LinearLayout(this);
progressLayout.setOrientation(LinearLayout.VERTICAL);
progressLayout.setGravity(Gravity.CENTER);
progressLayout.setPadding(50, 50, 50, 50);
progressLayout.setBackgroundColor(Color.parseColor("#1E293B"));

ProgressBar pb = new ProgressBar(this);
progressLayout.addView(pb);

TextView msg = new TextView(this);
msg.setText("\nBuilding Android 13 Verified Clone #" + cloneNum + "...\nChecking DEX String Sort Order & Aligning 16KB...");
msg.setTextColor(Color.WHITE);
msg.setGravity(Gravity.CENTER);
progressLayout.addView(msg);

AlertDialog progressDialog = new AlertDialog.Builder(this)
.setView(progressLayout)
.setCancelable(false)
.create();
progressDialog.show();

new Thread(() -> {
List<File> signedApksToInstall = new ArrayList<>();
try {
KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
kpg.initialize(2048);
KeyPair kp = kpg.generateKeyPair();

X500Name issuer = new X500Name("CN=Device_" + UUID.randomUUID().toString().substring(0, 8) + ", O=TechZoneCloner");
BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
Date notBefore = new Date(System.currentTimeMillis() - 86400000L);
Date notAfter = new Date(System.currentTimeMillis() + 315360000000L);

X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
issuer, serial, notBefore, notAfter, issuer, kp.getPublic());
ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(kp.getPrivate());
X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
"CLONE_KEY", kp.getPrivate(), Collections.singletonList(cert)).build();

List<File> sourceApks = new ArrayList<>();
sourceApks.add(new File(item.info.sourceDir));
if (item.info.splitSourceDirs != null) {
for (String split : item.info.splitSourceDirs) {
sourceApks.add(new File(split));
}
}

for (int i = 0; i < sourceApks.size(); i++) {
File src = sourceApks.get(i);
File unsignedTemp = new File(getCacheDir(), "unsigned_part_" + i + ".apk");
File signedPart = new File(getCacheDir(), "signed_part_" + i + ".apk");
if (unsignedTemp.exists()) unsignedTemp.delete();
if (signedPart.exists()) signedPart.delete();

boolean isBaseApk = (i == 0);
repackageSingleApk(src, unsignedTemp, item.packageName, newPkg, item.label, customAppName, isBaseApk, cloneNum);

System.gc();

new ApkSigner.Builder(Collections.singletonList(signerConfig))
.setInputApk(unsignedTemp)
.setOutputApk(signedPart)
.setV1SigningEnabled(true)
.setV2SigningEnabled(true)
.setV3SigningEnabled(true)
.build()
.sign();

unsignedTemp.delete();
signedApksToInstall.add(signedPart);
}

if (signedApksToInstall.size() == 1) {
File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
if (!downloadsDir.exists()) downloadsDir.mkdirs();
File outDownload = new File(downloadsDir, item.packageName + "_Clone" + cloneNum + ".apk");
copyFile(signedApksToInstall.get(0), outDownload);
}

runOnUiThread(() -> {
progressDialog.dismiss();
installSplitApkSession(signedApksToInstall);
});

} catch (Throwable e) {
e.printStackTrace();
runOnUiThread(() -> {
progressDialog.dismiss();
new AlertDialog.Builder(this)
.setTitle("Build Error")
.setMessage("Failed: " + e.toString())
.setPositiveButton("OK", null)
.show();
});
}
}).start();
}

private void installSplitApkSession(List<File> apkFiles) {
try {
PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
PackageInstaller.SessionParams.MODE_FULL_INSTALL);

int sessionId = packageInstaller.createSession(params);
PackageInstaller.Session session = packageInstaller.openSession(sessionId);

byte[] buffer = new byte[32768];
for (int i = 0; i < apkFiles.size(); i++) {
File apk = apkFiles.get(i);
try (OutputStream out = session.openWrite("split_" + i + ".apk", 0, apk.length());
FileInputStream in = new FileInputStream(apk)) {
int len;
while ((len = in.read(buffer)) != -1) {
out.write(buffer, 0, len);
}
session.fsync(out);
}
}

Intent callbackIntent = new Intent(this, MainActivity.class);
callbackIntent.setAction("ACTION_INSTALL_RESULT");
int flags = PendingIntent.FLAG_UPDATE_CURRENT;
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
flags |= PendingIntent.FLAG_MUTABLE;
}
PendingIntent pendingIntent = PendingIntent.getActivity(this, sessionId, callbackIntent, flags);

session.commit(pendingIntent.getIntentSender());
session.close();

} catch (Throwable e) {
e.printStackTrace();
new AlertDialog.Builder(this)
.setTitle("Installer Error")
.setMessage(e.toString())
.setPositiveButton("OK", null)
.show();
}
}

@Override
protected void onNewIntent(Intent intent) {
super.onNewIntent(intent);
handleInstallResultIntent(intent);
}

private void handleInstallResultIntent(Intent intent) {
if (intent != null && "ACTION_INSTALL_RESULT".equals(intent.getAction())) {
intent.setAction(null);
Bundle extras = intent.getExtras();
if (extras != null) {
int status = extras.getInt(PackageInstaller.EXTRA_STATUS, -999);
String statusMsg = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);

if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
if (confirmIntent != null) {
startActivity(confirmIntent);
}
} else if (status == PackageInstaller.STATUS_SUCCESS) {
Toast.makeText(this, "🎉 Clone Installed Successfully!", Toast.LENGTH_LONG).show();
if (adapter != null) adapter.notifyDataSetChanged();
} else if (status != -999) {
new AlertDialog.Builder(this)
.setTitle("❌ Exact System Install Error")
.setMessage("Status Code: " + status + "\n\nExact Reason:\n" + statusMsg)
.setPositiveButton("Copy / OK", null)
.show();
}
}
}
}

private void repackageSingleApk(File srcApk, File dstApk, String oldPkg, String newPkg, String origLabel, String customLabel, boolean isBaseApk, int cloneNum) throws Exception {
try (ZipFile zipFile = new ZipFile(srcApk);
CountingOutputStream cos = new CountingOutputStream(new BufferedOutputStream(new FileOutputStream(dstApk), 65536));
ZipOutputStream zos = new ZipOutputStream(cos)) {

zos.setLevel(Deflater.DEFAULT_COMPRESSION);

List<String> modifiedAuthorities = new ArrayList<>();
ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
byte[] patchedManifestData = null;
if (manifestEntry != null) {
byte[] rawManifest = readAllBytes(zipFile.getInputStream(manifestEntry));
patchedManifestData = patchAxmlManifestSafely(rawManifest, oldPkg, newPkg, modifiedAuthorities);
}

Enumeration<? extends ZipEntry> entries = zipFile.entries();

while (entries.hasMoreElements()) {
ZipEntry entry = entries.nextElement();
String name = entry.getName();

if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.equals("META-INF/MANIFEST.MF"))) {
continue;
}
if (name.equals("stamp-cert-sha256")) {
continue;
}

byte[] data;
if (name.equals("AndroidManifest.xml") && patchedManifestData != null) {
data = patchedManifestData;
} else {
data = readAllBytes(zipFile.getInputStream(entry));
if (name.equals("resources.arsc") && isBaseApk) {
data = patchArscAppLabelSafely(data, origLabel, customLabel);
} else if (name.endsWith(".dex")) {
data = patchDexAndroid13Safe(data, oldPkg, newPkg, modifiedAuthorities, cloneNum);
}
}

ZipEntry newEntry = new ZipEntry(name);

boolean isNativeLib = name.endsWith(".so") || name.startsWith("lib/");
boolean isArsc = name.equals("resources.arsc");

if (isNativeLib || isArsc || entry.getMethod() == ZipEntry.STORED) {
newEntry.setMethod(ZipEntry.STORED);
newEntry.setSize(data.length);
newEntry.setCompressedSize(data.length);
CRC32 crc = new CRC32();
crc.update(data);
newEntry.setCrc(crc.getValue());

int align = isNativeLib ? 16384 : 4;
byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

long dataStartOffset = cos.count + 30 + nameBytes.length;
int padding = (int) ((align - (dataStartOffset % align)) % align);

if (padding > 0) {
newEntry.setExtra(new byte[padding]);
}
} else {
newEntry.setMethod(ZipEntry.DEFLATED);
}

zos.putNextEntry(newEntry);
zos.write(data);
zos.closeEntry();
zos.flush();
}
}
}

private byte[] patchDexAndroid13Safe(byte[] dexData, String oldPkg, String newPkg, List<String> modifiedAuthorities, int cloneNum) {
try {
if (dexData == null || dexData.length < 112) return dexData;
if (dexData[0] != 'd' || dexData[1] != 'e' || dexData[2] != 'x' || dexData[3] != '\n') {
return dexData;
}
boolean changed = false;

for (String origAuth : modifiedAuthorities) {
if (origAuth.contains(oldPkg)) {
String newAuth = origAuth.replace(oldPkg, newPkg);
if (origAuth.length() == newAuth.length()) {
if (replaceDexStringSortSafe(dexData, origAuth, newAuth)) {
changed = true;
}
}
}
}

String[] commonSuffixes = {
".notifee-init-provider",
".fileprovider",
".provider",
".androidx-startup",
".firebaseinitprovider",
".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
};
for (String sfx : commonSuffixes) {
if (replaceDexStringSortSafe(dexData, oldPkg + sfx, newPkg + sfx)) {
changed = true;
}
}

char cDigit = (char) ('0' + (cloneNum % 10));
String[] idCandidates = {
"android_ic" + cDigit,
"android_i" + cDigit + "d",
"android_i_" + cDigit
};
for (String cand : idCandidates) {
if (replaceDexStringSortSafe(dexData, "android_id", cand)) {
changed = true;
break;
}
}

if (changed) {
MessageDigest md = MessageDigest.getInstance("SHA-1");
md.update(dexData, 32, dexData.length - 32);
byte[] sha1 = md.digest();
System.arraycopy(sha1, 0, dexData, 12, 20);

Adler32 adler = new Adler32();
adler.update(dexData, 12, dexData.length - 12);
int checksum = (int) adler.getValue();
ByteBuffer.wrap(dexData, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(checksum);
}
} catch (Exception ignored) {
}
return dexData;
}

private boolean replaceDexStringSortSafe(byte[] dexData, String targetStr, String replacementStr) {
try {
byte[] targetBytes = targetStr.getBytes(StandardCharsets.UTF_8);
byte[] repBytes = replacementStr.getBytes(StandardCharsets.UTF_8);
if (targetBytes.length != repBytes.length || targetBytes.length == 0) return false;

ByteBuffer buf = ByteBuffer.wrap(dexData).order(ByteOrder.LITTLE_ENDIAN);
int stringIdsSize = buf.getInt(56);
int stringIdsOff = buf.getInt(60);
if (stringIdsSize <= 0 || stringIdsOff <= 0 || stringIdsOff + (stringIdsSize * 4) > dexData.length) {
return false;
}

boolean modifiedAny = false;
byte firstByte = targetBytes[0];

for (int i = 0; i < stringIdsSize; i++) {
int strDataOff = buf.getInt(stringIdsOff + (i * 4));
if (strDataOff <= 0 || strDataOff + 2 >= dexData.length) continue;

int b0 = dexData[strDataOff] & 0xFF;
if ((b0 & 0x80) == 0 && dexData[strDataOff + 1] != firstByte) {
continue;
}

int[] posAndLen = getDexStringStartAndLength(dexData, strDataOff);
if (posAndLen == null) continue;
int start = posAndLen[0];
int byteLen = posAndLen[1];

if (byteLen == targetBytes.length && matchesAt(dexData, start, targetBytes)) {
boolean okPrev = true;
boolean okNext = true;
if (i > 0) {
int prevOff = buf.getInt(stringIdsOff + ((i - 1) * 4));
byte[] prevBytes = readDexStringBytes(dexData, prevOff);
if (prevBytes != null && compareUnsignedLex(prevBytes, repBytes) >= 0) {
okPrev = false;
}
}
if (i + 1 < stringIdsSize) {
int nextOff = buf.getInt(stringIdsOff + ((i + 1) * 4));
byte[] nextBytes = readDexStringBytes(dexData, nextOff);
if (nextBytes != null && compareUnsignedLex(repBytes, nextBytes) >= 0) {
okNext = false;
}
}
if (okPrev && okNext) {
System.arraycopy(repBytes, 0, dexData, start, repBytes.length);
modifiedAny = true;
}
}
}
return modifiedAny;
} catch (Exception e) {
return false;
}
}

private int[] getDexStringStartAndLength(byte[] dexData, int strDataOff) {
if (strDataOff <= 0 || strDataOff >= dexData.length) return null;
int p = strDataOff;
while (p < dexData.length && (dexData[p] & 0x80) != 0) {
p++;
}
p++;
if (p >= dexData.length) return null;
int start = p;
while (p < dexData.length && dexData[p] != 0) {
p++;
}
return new int[]{start, p - start};
}

private byte[] readDexStringBytes(byte[] dexData, int strDataOff) {
int[] posAndLen = getDexStringStartAndLength(dexData, strDataOff);
if (posAndLen == null) return null;
byte[] out = new byte[posAndLen[1]];
System.arraycopy(dexData, posAndLen[0], out, 0, posAndLen[1]);
return out;
}

private boolean matchesAt(byte[] src, int offset, byte[] target) {
if (offset + target.length > src.length) return false;
for (int i = 0; i < target.length; i++) {
if (src[offset + i] != target[i]) return false;
}
return true;
}

private int compareUnsignedLex(byte[] a, byte[] b) {
int minLen = Math.min(a.length, b.length);
for (int i = 0; i < minLen; i++) {
int va = a[i] & 0xFF;
int vb = b[i] & 0xFF;
if (va != vb) return va - vb;
}
return a.length - b.length;
}

private byte[] patchArscAppLabelSafely(byte[] arscData, String origLabel, String customLabel) {
try {
if (arscData == null || arscData.length < 32 || origLabel == null || customLabel == null || origLabel.isEmpty() || customLabel.isEmpty()) {
return arscData;
}
byte[] origUtf8 = origLabel.getBytes(StandardCharsets.UTF_8);
byte[] newUtf8 = customLabel.getBytes(StandardCharsets.UTF_8);

if (origLabel.length() < 128 && origUtf8.length < 128 && customLabel.length() <= origLabel.length() && newUtf8.length <= origUtf8.length) {
byte[] patternUtf8 = new byte[2 + origUtf8.length + 1];
patternUtf8[0] = (byte) origLabel.length();
patternUtf8[1] = (byte) origUtf8.length;
System.arraycopy(origUtf8, 0, patternUtf8, 2, origUtf8.length);
patternUtf8[patternUtf8.length - 1] = 0;

byte[] replacementUtf8 = new byte[patternUtf8.length];
replacementUtf8[0] = (byte) customLabel.length();
replacementUtf8[1] = (byte) newUtf8.length;
System.arraycopy(newUtf8, 0, replacementUtf8, 2, newUtf8.length);

replaceBytesInPlace(arscData, patternUtf8, replacementUtf8);
}

if (customLabel.length() <= origLabel.length()) {
StringBuilder padded = new StringBuilder(customLabel);
while (padded.length() < origLabel.length()) {
padded.append(" ");
}
byte[] oldLblUtf16 = (origLabel + "\0").getBytes(StandardCharsets.UTF_16LE);
byte[] newLblUtf16 = (padded.toString() + "\0").getBytes(StandardCharsets.UTF_16LE);
replaceBytesInPlace(arscData, oldLblUtf16, newLblUtf16);
}
} catch (Exception ignored) {
}
return arscData;
}

private byte[] patchAxmlManifestSafely(byte[] data, String oldPkg, String newPkg, List<String> outModifiedAuthorities) {
try {
ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

int offset = 8;
int fileSize = buf.getInt(4);

int authoritiesResIdx = -1;
int sharedUserIdResIdx = -1;
int extractNativeLibsIdx = -1;
int stringPoolOffset = -1;

while (offset + 8 <= fileSize && offset + 8 <= data.length) {
int chunkType = buf.getShort(offset) & 0xFFFF;
int headerSize = buf.getShort(offset + 2) & 0xFFFF;
int chunkSize = buf.getInt(offset + 4);
if (chunkSize <= 0) break;

if (chunkType == 0x0001) {
stringPoolOffset = offset;
patchExactPackageAndProvidersInPool(data, buf, stringPoolOffset, oldPkg, newPkg, outModifiedAuthorities);
} else if (chunkType == 0x0180) {
int count = (chunkSize - headerSize) / 4;
for (int i = 0; i < count; i++) {
int resId = buf.getInt(offset + headerSize + (i * 4));
if (resId == 0x01010018) authoritiesResIdx = i;
else if (resId == 0x0101000b) sharedUserIdResIdx = i;
else if (resId == 0x010104ea) extractNativeLibsIdx = i;
}
} else if (chunkType == 0x0102) {
int attrStart = buf.getShort(offset + 24) & 0xFFFF;
int attrSize = buf.getShort(offset + 26) & 0xFFFF;
int attrCount = buf.getShort(offset + 28) & 0xFFFF;
int attrsBase = offset + 16 + attrStart;

for (int i = 0; i < attrCount; i++) {
int attrOffset = attrsBase + (i * attrSize);
if (attrOffset + 20 > data.length) break;

int nameIdx = buf.getInt(attrOffset + 4);
int rawValueIdx = buf.getInt(attrOffset + 8);
int typedValueIdx = buf.getInt(attrOffset + 16);

if (nameIdx == authoritiesResIdx && authoritiesResIdx != -1 && stringPoolOffset != -1) {
if (rawValueIdx >= 0) forceModifyAuthorityInPool(data, buf, stringPoolOffset, rawValueIdx, oldPkg, newPkg, outModifiedAuthorities);
if (typedValueIdx >= 0 && typedValueIdx != rawValueIdx) forceModifyAuthorityInPool(data, buf, stringPoolOffset, typedValueIdx, oldPkg, newPkg, outModifiedAuthorities);
} else if (nameIdx == sharedUserIdResIdx && sharedUserIdResIdx != -1) {
buf.putInt(attrOffset + 4, -1);
} else if (nameIdx == extractNativeLibsIdx && extractNativeLibsIdx != -1) {
buf.putInt(attrOffset + 8, -1);
buf.putShort(attrOffset + 12, (short) 8);
buf.put(attrOffset + 14, (byte) 0);
buf.put(attrOffset + 15, (byte) 0x12);
buf.putInt(attrOffset + 16, -1);
}
}
}
offset += chunkSize;
}

} catch (Exception ignored) {
}
return data;
}

private void patchExactPackageAndProvidersInPool(byte[] data, ByteBuffer buf, int poolOffset, String oldPkg, String newPkg, List<String> outModifiedAuthorities) {
try {
int stringCount = buf.getInt(poolOffset + 8);
int flags = buf.getInt(poolOffset + 16);
boolean isUtf8 = (flags & (1 << 8)) != 0;
int stringsStart = poolOffset + buf.getInt(poolOffset + 20);
int offsetTable = poolOffset + 28;

String prefix = oldPkg + ".";
String newPrefix = newPkg + ".";

for (int i = 0; i < stringCount; i++) {
int strOffset = stringsStart + buf.getInt(offsetTable + (i * 4));
if (isUtf8) {
int len1 = data[strOffset] & 0xFF;
int skip = ((len1 & 0x80) != 0) ? 2 : 1;
int len2 = data[strOffset + skip] & 0xFF;
int skip2 = ((len2 & 0x80) != 0) ? 2 : 1;
int actualStart = strOffset + skip + skip2;
int byteLen = ((len2 & 0x80) != 0) ? (((len2 & 0x7F) << 8) | (data[strOffset + skip + 1] & 0xFF)) : len2;
if (byteLen <= 0 || actualStart + byteLen > data.length) continue;

String val = new String(data, actualStart, byteLen, StandardCharsets.UTF_8);
if (val.equals(oldPkg)) {
System.arraycopy(newPkg.getBytes(StandardCharsets.UTF_8), 0, data, actualStart, byteLen);
} else if (shouldPatchManifestString(val, prefix)) {
outModifiedAuthorities.add(val);
byte[] slice = new byte[byteLen];
System.arraycopy(data, actualStart, slice, 0, byteLen);
replaceBytesInPlace(slice, prefix.getBytes(StandardCharsets.UTF_8), newPrefix.getBytes(StandardCharsets.UTF_8));
System.arraycopy(slice, 0, data, actualStart, byteLen);
}
} else {
int len1 = buf.getShort(strOffset) & 0xFFFF;
int skip = ((len1 & 0x8000) != 0) ? 4 : 2;
int charLen = ((len1 & 0x8000) != 0) ? (((len1 & 0x7FFF) << 16) | (buf.getShort(strOffset + 2) & 0xFFFF)) : len1;
int actualStart = strOffset + skip;
int byteLen = charLen * 2;
if (byteLen <= 0 || actualStart + byteLen > data.length) continue;

String val = new String(data, actualStart, byteLen, StandardCharsets.UTF_16LE);
if (val.equals(oldPkg)) {
System.arraycopy(newPkg.getBytes(StandardCharsets.UTF_16LE), 0, data, actualStart, byteLen);
} else if (shouldPatchManifestString(val, prefix)) {
outModifiedAuthorities.add(val);
byte[] slice = new byte[byteLen];
System.arraycopy(data, actualStart, slice, 0, byteLen);
replaceBytesInPlace(slice, prefix.getBytes(StandardCharsets.UTF_16LE), newPrefix.getBytes(StandardCharsets.UTF_16LE));
System.arraycopy(slice, 0, data, actualStart, byteLen);
}
}
}
} catch (Exception ignored) {
}
}

private boolean shouldPatchManifestString(String val, String prefix) {
if (!val.startsWith(prefix)) return false;
String suffix = val.substring(prefix.length());
String lower = suffix.toLowerCase();
if (suffix.contains("-") || suffix.contains(";")) return true;
if (lower.contains("provider") || lower.contains("permission") || lower.contains("authority") || lower.contains("workmanager") || lower.contains("lifecycle") || lower.contains("firebaseinit")) {
return true;
}
if (!suffix.contains(".") && suffix.equals(lower)) {
return true;
}
return false;
}

private void forceModifyAuthorityInPool(byte[] data, ByteBuffer buf, int poolOffset, int strIndex, String oldPkg, String newPkg, List<String> outModifiedAuthorities) {
try {
int stringCount = buf.getInt(poolOffset + 8);
if (strIndex < 0 || strIndex >= stringCount) return;

int flags = buf.getInt(poolOffset + 16);
boolean isUtf8 = (flags & (1 << 8)) != 0;
int stringsStart = poolOffset + buf.getInt(poolOffset + 20);
int offsetTable = poolOffset + 28;
int strOffset = stringsStart + buf.getInt(offsetTable + (strIndex * 4));
char cloneChar = newPkg.charAt(newPkg.length() - 1);

if (isUtf8) {
int len1 = data[strOffset] & 0xFF;
int skip = ((len1 & 0x80) != 0) ? 2 : 1;
int len2 = data[strOffset + skip] & 0xFF;
int skip2 = ((len2 & 0x80) != 0) ? 2 : 1;
int actualStart = strOffset + skip + skip2;
int byteLen = ((len2 & 0x80) != 0) ? (((len2 & 0x7F) << 8) | (data[strOffset + skip + 1] & 0xFF)) : len2;
if (byteLen <= 0 || actualStart + byteLen > data.length) return;

String origVal = new String(data, actualStart, byteLen, StandardCharsets.UTF_8);
outModifiedAuthorities.add(origVal);

byte[] slice = new byte[byteLen];
System.arraycopy(data, actualStart, slice, 0, byteLen);
boolean changed = replaceBytesInPlace(slice, oldPkg.getBytes(StandardCharsets.UTF_8), newPkg.getBytes(StandardCharsets.UTF_8));
if (!changed && !origVal.contains(newPkg)) {
slice[byteLen - 1] = (byte) cloneChar;
}
System.arraycopy(slice, 0, data, actualStart, byteLen);
} else {
int len1 = buf.getShort(strOffset) & 0xFFFF;
int skip = ((len1 & 0x8000) != 0) ? 4 : 2;
int charLen = ((len1 & 0x8000) != 0) ? (((len1 & 0x7FFF) << 16) | (buf.getShort(strOffset + 2) & 0xFFFF)) : len1;
int actualStart = strOffset + skip;
int byteLen = charLen * 2;
if (byteLen < 2 || actualStart + byteLen > data.length) return;

String origVal = new String(data, actualStart, byteLen, StandardCharsets.UTF_16LE);
outModifiedAuthorities.add(origVal);

byte[] slice = new byte[byteLen];
System.arraycopy(data, actualStart, slice, 0, byteLen);
boolean changed = replaceBytesInPlace(slice, oldPkg.getBytes(StandardCharsets.UTF_16LE), newPkg.getBytes(StandardCharsets.UTF_16LE));
if (!changed && !origVal.contains(newPkg)) {
slice[byteLen - 2] = (byte) cloneChar;
slice[byteLen - 1] = 0;
}
System.arraycopy(slice, 0, data, actualStart, byteLen);
}
} catch (Exception ignored) {
}
}

private boolean replaceBytesInPlace(byte[] source, byte[] target, byte[] replacement) {
if (target.length != replacement.length) return false;
boolean found = false;
for (int i = 0; i <= source.length - target.length; i++) {
boolean match = true;
for (int j = 0; j < target.length; j++) {
if (source[i + j] != target[j]) {
match = false;
break;
}
}
if (match) {
System.arraycopy(replacement, 0, source, i, replacement.length);
i += target.length - 1;
found = true;
}
}
return found;
}

private void copyFile(File src, File dst) throws Exception {
try (InputStream in = new FileInputStream(src);
OutputStream out = new FileOutputStream(dst)) {
byte[] buf = new byte[32768];
int len;
while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
}
}

private byte[] readAllBytes(InputStream is) throws Exception {
ByteArrayOutputStream bos = new ByteArrayOutputStream();
byte[] buf = new byte[16384];
int len;
while ((len = is.read(buf)) > 0) bos.write(buf, 0, len);
return bos.toByteArray();
}
}
