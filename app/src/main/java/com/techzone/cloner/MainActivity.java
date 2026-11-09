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
        title.setText("⚡ TechZone Pro APK Cloner v3.8");
        title.setTextColor(Color.parseColor("#38BDF8"));
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        statusBanner = new TextView(this);
        statusBanner.setText("Platform-Check Bypass + Original Graphics + Split Installer");
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
        searchLp.setLayoutParams(searchLp);
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
            nameView.setText(item.label + " -> " + item.label + " " + currentCloneNum);
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
                badge.setText("✔ Clone #" + currentCloneNum + " Installed (Stealth Mode)");
                badge.setTextColor(Color.parseColor("#10B981"));
            } else {
                badge.setText(item.isSplitApk ? "🛡️ Split Bundle (Stealth + Full Icons)" : "🛡️ Standalone (Stealth + New Identity)");
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
        nameLabel.setText("✏️ Custom Clone App Name:");
        nameLabel.setTextColor(Color.parseColor("#10B981"));
        nameLabel.setTextSize(13);
        nameLabel.setTypeface(null, Typeface.BOLD);
        nameLabel.setPadding(0, 24, 0, 10);
        panel.addView(nameLabel);

        EditText customNameInput = new EditText(this);
        customNameInput.setText(item.label + " " + currentCloneNum);
        customNameInput.setTextColor(Color.WHITE);
        customNameInput.setTextSize(15);
        customNameInput.setBackground(createRoundedBg("#1E293B", 18, "#38BDF8", 2));
        customNameInput.setPadding(28, 20, 28, 20);
        panel.addView(customNameInput);

        int splitCount = (item.info.splitSourceDirs != null) ? item.info.splitSourceDirs.length : 0;

        TextView infoBox = new TextView(this);
        infoBox.setText("📦 Package: " + targetClonePkg +
                "\n🕵️ Anti-Detect: Whitelists Cloner Installer + Spoofs Package Check" +
                "\n🆔 Identity: New Android_ID, Serial & Device Fingerprint (#" + currentCloneNum + ")" +
                "\n🎨 Graphics: Preserves Original Split ARSC (" + (splitCount + 1) + " APKs)");
        infoBox.setTextColor(Color.parseColor("#38BDF8"));
        infoBox.setTextSize(11);
        infoBox.setBackground(createRoundedBg("#1E293B", 18, "#334155", 2));
        infoBox.setPadding(28, 24, 28, 24);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.setMargins(0, 24, 0, 24);
        infoBox.setLayoutParams(boxLp);
        panel.addView(infoBox);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();

        Button btnBuild = createActionButton("🔨 1. Build & Install Stealth Clone", "#10B981");
        btnBuild.setOnClickListener(v -> {
            String chosenName = customNameInput.getText().toString().trim();
            if (chosenName.isEmpty()) chosenName = item.label + " " + currentCloneNum;
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
        msg.setText("\nBypassing Platform Verification & Preserving Split Graphics...\nPlease wait...");
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

                X500Name issuer = new X500Name("CN=GooglePlay_" + UUID.randomUUID().toString().substring(0, 8) + ", O=Android");
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
                    installSplitApkSession(signedApksToInstall, newPkg);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Build Error")
                            .setMessage("Failed: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private void installSplitApkSession(List<File> apkFiles, String targetPkg) {
        try {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);

            params.setAppPackageName(targetPkg);

            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            for (int i = 0; i < apkFiles.size(); i++) {
                File apk = apkFiles.get(i);
                try (OutputStream out = session.openWrite("split_" + i + ".apk", 0, apk.length());
                     FileInputStream in = new FileInputStream(apk)) {
                    byte[] buffer = new byte[16384];
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

        } catch (Exception e) {
            e.printStackTrace();
            new AlertDialog.Builder(this)
                    .setTitle("Installer Error")
                    .setMessage(e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && "ACTION_INSTALL_RESULT".equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                int status = extras.getInt(PackageInstaller.EXTRA_STATUS);
                String statusMsg = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE);

                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirmIntent = (Intent) extras.get(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        startActivity(confirmIntent);
                    }
                } else if (status == PackageInstaller.STATUS_SUCCESS) {
                    Toast.makeText(this, "🎉 Stealth Clone Installed Successfully!", Toast.LENGTH_LONG).show();
                    adapter.notifyDataSetChanged();
                } else {
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
             CountingOutputStream cos = new CountingOutputStream(new FileOutputStream(dstApk));
             ZipOutputStream zos = new ZipOutputStream(cos)) {

            zos.setLevel(Deflater.DEFAULT_COMPRESSION);

            List<String> modifiedAuthorities = new ArrayList<>();
            ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
            byte[] patchedManifestData = null;
            if (manifestEntry != null) {
                byte[] rawManifest = readAllBytes(zipFile.getInputStream(manifestEntry));
                patchedManifestData = patchAxmlManifestSafely(rawManifest, oldPkg, newPkg, customLabel, isBaseApk, modifiedAuthorities);
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
                    if (name.equals("resources.arsc")) {
                        // DO NOT rename the package inside resources.arsc!
                        // Keeping the original resource package name allows Split APK drawables (like the girl splash icon) to link and load properly!
                        data = patchArscLabelOnly(data, origLabel, customLabel);
                    } else if (name.endsWith(".dex")) {
                        data = patchDexStealthAndAuthorities(data, oldPkg, newPkg, modifiedAuthorities, cloneNum);
                    } else if (name.endsWith(".bundle") || name.startsWith("assets/")) {
                        data = patchReactNativeBundleSafely(data, oldPkg, newPkg, cloneNum);
                    }
                }

                ZipEntry newEntry = new ZipEntry(name);

                boolean isNativeLib = name.endsWith(".so") || name.startsWith("lib/");
                boolean isArsc = name.equals("resources.arsc");

                // Compress .so files with DEFLATED (paired with extractNativeLibs=true) so alignment never fails
                if (!isNativeLib && (isArsc || entry.getMethod() == ZipEntry.STORED)) {
                    newEntry.setMethod(ZipEntry.STORED);
                    newEntry.setSize(data.length);
                    newEntry.setCompressedSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    newEntry.setCrc(crc.getValue());

                    int align = 4;
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

    private byte[] patchDexStealthAndAuthorities(byte[] dexData, String oldPkg, String newPkg, List<String> modifiedAuthorities, int cloneNum) {
        try {
            if (dexData.length < 112) return dexData;
            boolean changed = false;

            // 1. Sync Provider Authorities
            for (String origAuth : modifiedAuthorities) {
                if (origAuth.contains(oldPkg)) {
                    String newAuth = origAuth.replace(oldPkg, newPkg);
                    if (origAuth.length() == newAuth.length()) {
                        if (replaceBytesInPlace(dexData, origAuth.getBytes(StandardCharsets.UTF_8), newAuth.getBytes(StandardCharsets.UTF_8))) {
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
                byte[] oldBytes = (oldPkg + sfx).getBytes(StandardCharsets.UTF_8);
                byte[] newBytes = (newPkg + sfx).getBytes(StandardCharsets.UTF_8);
                if (replaceBytesInPlace(dexData, oldBytes, newBytes)) {
                    changed = true;
                }
            }

            // 2. Bypass Package Name Anti-Clone Check in DEX String Pool
            // In DEX MUTF-8, exact strings are preceded by their length byte (e.g., 24 = 0x18 for "com.et3arraf.Et3arrafApp") and followed by \0
            byte[] exactOldPkgDex = (oldPkg + "\0").getBytes(StandardCharsets.UTF_8);
            byte[] exactNewPkgDex = (newPkg + "\0").getBytes(StandardCharsets.UTF_8);
            if (replaceBytesInPlace(dexData, exactOldPkgDex, exactNewPkgDex)) {
                changed = true;
            }

            // 3. CRITICAL FIX FOR "Allowed Platforms Only" (المنصات المسموح بها):
            // When our Cloner installs the app, Android sets getInstallerPackageName() = "com.techzone.cloner" (19 chars).
            // The app checks if getInstallerPackageName().equals("com.android.vending") (19 chars).
            // So we replace "com.android.vending" INSIDE the target app's DEX with "com.techzone.cloner"!
            // Now when the app checks if it was installed from the allowed store, it matches "com.techzone.cloner" and passes!
            byte[] playStoreBytes = "com.android.vending".getBytes(StandardCharsets.UTF_8);
            byte[] clonerPkgBytes = "com.techzone.cloner".getBytes(StandardCharsets.UTF_8);
            if (replaceBytesInPlace(dexData, playStoreBytes, clonerPkgBytes)) {
                changed = true;
            }

            // 4. Device Identity Spoofing (New Android ID, Advertising ID, Serial & Hardware Profile per Clone)
            String cNumStr = String.valueOf(cloneNum % 10);
            if (replaceBytesInPlace(dexData,
                    ("android_id\0").getBytes(StandardCharsets.UTF_8),
                    ("tz_aid_c0" + cNumStr + "\0").getBytes(StandardCharsets.UTF_8))) {
                changed = true;
            }
            if (replaceBytesInPlace(dexData,
                    ("advertising_id\0").getBytes(StandardCharsets.UTF_8),
                    ("tz_adid_cln_0" + cNumStr + "\0").getBytes(StandardCharsets.UTF_8))) {
                changed = true;
            }
            if (replaceBytesInPlace(dexData,
                    ("ro.serialno\0").getBytes(StandardCharsets.UTF_8),
                    ("tz.serial0" + cNumStr + "\0").getBytes(StandardCharsets.UTF_8))) {
                changed = true;
            }
            if (replaceBytesInPlace(dexData,
                    ("ro.boot.serialno\0").getBytes(StandardCharsets.UTF_8),
                    ("tz.boot.serial0" + cNumStr + "\0").getBytes(StandardCharsets.UTF_8))) {
                changed = true;
            }

            // Recalculate DEX SHA-1 Signature and Adler32 Checksum
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

    private byte[] patchReactNativeBundleSafely(byte[] bundleData, String oldPkg, String newPkg, int cloneNum) {
        try {
            if (bundleData == null || bundleData.length < 64) return bundleData;

            // 1. Replace quoted JS strings like "com.et3arraf.Et3arrafApp" or 'com.et3arraf.Et3arrafApp'
            replaceBytesInPlace(bundleData, ("\"" + oldPkg + "\"").getBytes(StandardCharsets.UTF_8), ("\"" + newPkg + "\"").getBytes(StandardCharsets.UTF_8));
            replaceBytesInPlace(bundleData, ("'" + oldPkg + "'").getBytes(StandardCharsets.UTF_8), ("'" + newPkg + "'").getBytes(StandardCharsets.UTF_8));

            // 2. Also whitelist our installer "com.techzone.cloner" inside the JS bundle if it checks installer in JS!
            replaceBytesInPlace(bundleData, "\"com.android.vending\"".getBytes(StandardCharsets.UTF_8), "\"com.techzone.cloner\"".getBytes(StandardCharsets.UTF_8));
            replaceBytesInPlace(bundleData, "'com.android.vending'".getBytes(StandardCharsets.UTF_8), "'com.techzone.cloner'".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        return bundleData;
    }

    private byte[] patchArscLabelOnly(byte[] arscData, String origLabel, String customLabel) {
        try {
            if (arscData == null || arscData.length < 32) return arscData;
            if (origLabel != null && customLabel != null && !origLabel.isEmpty() && customLabel.length() <= origLabel.length()) {
                StringBuilder padded = new StringBuilder(customLabel);
                while (padded.length() < origLabel.length()) {
                    padded.append(" ");
                }
                byte[] oldLblUtf8 = (origLabel + "\0").getBytes(StandardCharsets.UTF_8);
                byte[] newLblUtf8 = (padded.toString() + "\0").getBytes(StandardCharsets.UTF_8);
                replaceBytesInPlace(arscData, oldLblUtf8, newLblUtf8);

                byte[] oldLblUtf16 = (origLabel + "\0").getBytes(StandardCharsets.UTF_16LE);
                byte[] newLblUtf16 = (padded.toString() + "\0").getBytes(StandardCharsets.UTF_16LE);
                replaceBytesInPlace(arscData, oldLblUtf16, newLblUtf16);
            }
        } catch (Exception ignored) {
        }
        return arscData;
    }

    private byte[] patchAxmlManifestSafely(byte[] data, String oldPkg, String newPkg, String customLabel, boolean isBaseApk, List<String> outModifiedAuthorities) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            int offset = 8;
            int fileSize = buf.getInt(4);

            int labelResIdx = -1;
            int authoritiesResIdx = -1;
            int sharedUserIdResIdx = -1;
            int extractNativeLibsIdx = -1;
            int stringPoolOffset = -1;
            int customLabelStringIdx = -1;

            if (isBaseApk && customLabel != null && !customLabel.isEmpty()) {
                customLabelStringIdx = findAndOverwriteDummyStringInPool(data, buf, customLabel);
            }

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
                        if (resId == 0x01010001) labelResIdx = i;
                        else if (resId == 0x01010018) authoritiesResIdx = i;
                        else if (resId == 0x0101000b) sharedUserIdResIdx = i;
                        else if (resId == 0x010104ea) extractNativeLibsIdx = i;
                    }
                } else if (chunkType == 0x0102) {
                    int elemNameIdx = buf.getInt(offset + 20);
                    int attrStart = buf.getShort(offset + 24) & 0xFFFF;
                    int attrSize = buf.getShort(offset + 26) & 0xFFFF;
                    int attrCount = buf.getShort(offset + 28) & 0xFFFF;
                    int attrsBase = offset + 16 + attrStart;

                    String tagName = (stringPoolOffset != -1) ? getStringFromPool(data, buf, stringPoolOffset, elemNameIdx) : "";
                    boolean isAppTag = "application".equals(tagName);

                    for (int i = 0; i < attrCount; i++) {
                        int attrOffset = attrsBase + (i * attrSize);
                        if (attrOffset + 20 > data.length) break;

                        int nameIdx = buf.getInt(attrOffset + 4);
                        int rawValueIdx = buf.getInt(attrOffset + 8);
                        int typedValueIdx = buf.getInt(attrOffset + 16);

                        if (nameIdx == authoritiesResIdx && authoritiesResIdx != -1 && stringPoolOffset != -1) {
                            if (rawValueIdx >= 0) forceModifyAuthorityInPool(data, buf, stringPoolOffset, rawValueIdx, oldPkg, newPkg, outModifiedAuthorities);
                            if (typedValueIdx >= 0 && typedValueIdx != rawValueIdx) forceModifyAuthorityInPool(data, buf, stringPoolOffset, typedValueIdx, oldPkg, newPkg, outModifiedAuthorities);
                        } else if (isAppTag && nameIdx == labelResIdx && labelResIdx != -1 && customLabelStringIdx >= 0) {
                            buf.putInt(attrOffset + 8, customLabelStringIdx);
                            buf.putShort(attrOffset + 12, (short) 8);
                            buf.put(attrOffset + 14, (byte) 0);
                            buf.put(attrOffset + 15, (byte) 0x03);
                            buf.putInt(attrOffset + 16, customLabelStringIdx);
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

    private int findAndOverwriteDummyStringInPool(byte[] data, ByteBuffer buf, String newLabel) {
        try {
            int offset = 8;
            int fileSize = buf.getInt(4);
            while (offset + 8 <= fileSize && offset + 8 <= data.length) {
                int chunkType = buf.getShort(offset) & 0xFFFF;
                int chunkSize = buf.getInt(offset + 4);
                if (chunkSize <= 0) break;

                if (chunkType == 0x0001) {
                    int stringCount = buf.getInt(offset + 8);
                    int flags = buf.getInt(offset + 16);
                    boolean isUtf8 = (flags & (1 << 8)) != 0;
                    int stringsStart = offset + buf.getInt(offset + 20);
                    int offsetTable = offset + 28;

                    for (int i = 0; i < stringCount; i++) {
                        String s = getStringFromPool(data, buf, offset, i);
                        if (s.startsWith("http://schemas.android.com/apk/res-auto") || s.startsWith("androidx.core.app.CoreComponentFactory")) {
                            int strOffset = stringsStart + buf.getInt(offsetTable + (i * 4));
                            if (!isUtf8) {
                                int len1 = buf.getShort(strOffset) & 0xFFFF;
                                if ((len1 & 0x8000) == 0 && newLabel.length() <= len1) {
                                    int actualStart = strOffset + 2;
                                    buf.putShort(strOffset, (short) newLabel.length());
                                    byte[] newBytes = newLabel.getBytes(StandardCharsets.UTF_16LE);
                                    for (int k = 0; k < len1 * 2; k++) data[actualStart + k] = 0;
                                    System.arraycopy(newBytes, 0, data, actualStart, newBytes.length);
                                    return i;
                                }
                            } else {
                                byte[] newBytes = newLabel.getBytes(StandardCharsets.UTF_8);
                                int len1 = data[strOffset] & 0xFF;
                                int len2 = data[strOffset + 1] & 0xFF;
                                if ((len1 & 0x80) == 0 && (len2 & 0x80) == 0 && newBytes.length <= len2) {
                                    data[strOffset] = (byte) newLabel.length();
                                    data[strOffset + 1] = (byte) newBytes.length;
                                    int actualStart = strOffset + 2;
                                    for (int k = 0; k < len2; k++) data[actualStart + k] = 0;
                                    System.arraycopy(newBytes, 0, data, actualStart, newBytes.length);
                                    return i;
                                }
                            }
                        }
                    }
                    break;
                }
                offset += chunkSize;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private String getStringFromPool(byte[] data, ByteBuffer buf, int poolOffset, int strIndex) {
        try {
            if (strIndex < 0) return "";
            int stringCount = buf.getInt(poolOffset + 8);
            if (strIndex >= stringCount) return "";

            int flags = buf.getInt(poolOffset + 16);
            boolean isUtf8 = (flags & (1 << 8)) != 0;
            int stringsStart = poolOffset + buf.getInt(poolOffset + 20);
            int offsetTable = poolOffset + 28;
            int strOffset = stringsStart + buf.getInt(offsetTable + (strIndex * 4));

            if (isUtf8) {
                int len1 = data[strOffset] & 0xFF;
                int skip = ((len1 & 0x80) != 0) ? 2 : 1;
                int len2 = data[strOffset + skip] & 0xFF;
                int skip2 = ((len2 & 0x80) != 0) ? 2 : 1;
                int actualStart = strOffset + skip + skip2;
                int byteLen = ((len2 & 0x80) != 0) ? (((len2 & 0x7F) << 8) | (data[strOffset + skip + 1] & 0xFF)) : len2;
                return new String(data, actualStart, byteLen, StandardCharsets.UTF_8);
            } else {
                int len1 = buf.getShort(strOffset) & 0xFFFF;
                int skip = ((len1 & 0x8000) != 0) ? 4 : 2;
                int charLen = ((len1 & 0x8000) != 0) ? (((len1 & 0x7FFF) << 16) | (buf.getShort(strOffset + 2) & 0xFFFF)) : len1;
                int actualStart = strOffset + skip;
                int byteLen = charLen * 2;
                return new String(data, actualStart, byteLen, StandardCharsets.UTF_16LE);
            }
        } catch (Exception e) {
            return "";
        }
    }

    private void patchExactPackageAndProvidersInPool(byte[] data, ByteBuffer buf, int poolOffset, String oldPkg, String newPkg, List<String> outModifiedAuthorities) {
        try {
            int stringCount = buf.getInt(poolOffset + 8);
            int flags = buf.getInt(poolOffset + 16);
            boolean isUtf8 = (flags & (1 << 8)) != 0;
            int stringsStart = poolOffset + buf.getInt(offsetTableOffset(poolOffset) - 8);
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

    private int offsetTableOffset(int poolOffset) {
        return poolOffset + 28;
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
            byte[] buf = new byte[16384];
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
