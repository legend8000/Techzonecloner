package com.techzone.cloner;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

        // Header Card
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(createRoundedBg("#1E293B", 28, "#38BDF8", 2));
        header.setPadding(32, 28, 32, 28);

        TextView title = new TextView(this);
        title.setText("⚡ TechZone APK Re-Cloner v2.2");
        title.setTextColor(Color.parseColor("#38BDF8"));
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        statusBanner = new TextView(this);
        statusBanner.setText("True Binary AXML Patcher + Split APK Merger");
        statusBanner.setTextColor(Color.parseColor("#94A3B8"));
        statusBanner.setTextSize(12);
        statusBanner.setPadding(0, 8, 0, 20);
        header.addView(statusBanner);

        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView profLabel = new TextView(this);
        profLabel.setText("Clone Number:");
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

        // Search Box
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
            nameView.setText(item.label);
            nameView.setTextColor(Color.WHITE);
            nameView.setTextSize(16);
            nameView.setTypeface(null, Typeface.BOLD);
            textCol.addView(nameView);

            TextView pkgView = new TextView(MainActivity.this);
            pkgView.setText("Orig: " + item.packageName + "\nClone: " + targetClonePkg);
            pkgView.setTextColor(Color.parseColor("#94A3B8"));
            pkgView.setTextSize(11);
            textCol.addView(pkgView);

            TextView badge = new TextView(MainActivity.this);
            if (installed) {
                badge.setText("✔ Standalone Clone #" + currentCloneNum + " Installed on Phone");
                badge.setTextColor(Color.parseColor("#10B981"));
            } else {
                badge.setText(item.isSplitApk ? "⚡ Split APK (Binary Fix Ready)" : "○ Ready to Build Standalone APK");
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

        TextView infoBox = new TextView(this);
        infoBox.setText("📦 New Package: " + targetClonePkg +
                "\n🧩 Split Merger: " + (item.isSplitApk ? "Merging all splits + Fixing NativeLibs ID" : "Standard Single APK") +
                "\n🔑 Identity: Unique Signing Key -> New Android ID" +
                "\n📁 Output: Saved in Downloads folder");
        infoBox.setTextColor(Color.parseColor("#38BDF8"));
        infoBox.setTextSize(12);
        infoBox.setBackground(createRoundedBg("#1E293B", 18, "#334155", 2));
        infoBox.setPadding(28, 24, 28, 24);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.setMargins(0, 28, 0, 28);
        infoBox.setLayoutParams(boxLp);
        panel.addView(infoBox);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();

        Button btnBuild = createActionButton("🔨 1. Build & Merge Standalone Clone APK", "#10B981");
        btnBuild.setOnClickListener(v -> {
            dialog.dismiss();
            buildStandaloneCloneApk(item, targetClonePkg);
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

    private void buildStandaloneCloneApk(AppItem item, String newPkg) {
        LinearLayout progressLayout = new LinearLayout(this);
        progressLayout.setOrientation(LinearLayout.VERTICAL);
        progressLayout.setGravity(Gravity.CENTER);
        progressLayout.setPadding(50, 50, 50, 50);
        progressLayout.setBackgroundColor(Color.parseColor("#1E293B"));

        ProgressBar pb = new ProgressBar(this);
        progressLayout.addView(pb);

        TextView msg = new TextView(this);
        msg.setText("\nMerging Split APKs & Patching Binary Resource IDs...\nPlease wait (15-30 seconds)...");
        msg.setTextColor(Color.WHITE);
        msg.setGravity(Gravity.CENTER);
        progressLayout.addView(msg);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setView(progressLayout)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            try {
                File unsignedApk = new File(getCacheDir(), "unsigned_clone.apk");
                if (unsignedApk.exists()) unsignedApk.delete();

                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) downloadsDir.mkdirs();
                File signedApk = new File(downloadsDir, item.packageName + "_Clone" + currentCloneNum + ".apk");
                if (signedApk.exists()) signedApk.delete();

                // 1. Merge Base + All Split APKs & Binary Patch AndroidManifest.xml
                repackageAndPatchManifest(item.info, unsignedApk, item.packageName, newPkg);

                // 2. Generate Fresh Random RSA-2048 Key & Certificate
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

                // 3. Sign APK with V1 + V2 + V3 Schemes
                ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                        "CLONE_KEY", kp.getPrivate(), Collections.singletonList(cert)).build();

                new ApkSigner.Builder(Collections.singletonList(signerConfig))
                        .setInputApk(unsignedApk)
                        .setOutputApk(signedApk)
                        .setV1SigningEnabled(true)
                        .setV2SigningEnabled(true)
                        .setV3SigningEnabled(true)
                        .build()
                        .sign();

                unsignedApk.delete();

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "✅ Clone APK saved in Downloads: " + signedApk.getName(), Toast.LENGTH_LONG).show();
                    promptInstallApk(signedApk);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Clone Error")
                            .setMessage("Failed: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private void repackageAndPatchManifest(ApplicationInfo appInfo, File dstApk, String oldPkg, String newPkg) throws Exception {
        Set<String> addedEntries = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dstApk))) {
            // 1. Process Base APK first
            copyEntriesFromApk(new File(appInfo.sourceDir), zos, addedEntries, oldPkg, newPkg, true);

            // 2. Process all Split APKs (Native libs arm64-v8a, armeabi-v7a, dpi assets, lang splits)
            if (appInfo.splitSourceDirs != null) {
                for (String splitPath : appInfo.splitSourceDirs) {
                    copyEntriesFromApk(new File(splitPath), zos, addedEntries, oldPkg, newPkg, false);
                }
            }
        }
    }

    private void copyEntriesFromApk(File apkFile, ZipOutputStream zos, Set<String> addedEntries,
                                    String oldPkg, String newPkg, boolean isBaseApk) throws Exception {
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // Strip old signature files and stamp files
                if (name.startsWith("META-INF/") || name.equals("stamp-cert-sha256")) {
                    continue;
                }

                // Skip secondary manifests and resource tables from split APKs
                if (!isBaseApk && (name.equals("AndroidManifest.xml") || name.equals("resources.arsc"))) {
                    continue;
                }

                // Avoid duplicate zip entry crash
                if (addedEntries.contains(name)) {
                    continue;
                }
                addedEntries.add(name);

                byte[] data = readAllBytes(zipFile.getInputStream(entry));

                if (isBaseApk && name.equals("AndroidManifest.xml")) {
                    data = patchBinaryManifest(data, oldPkg, newPkg);
                }

                ZipEntry newEntry = new ZipEntry(name);
                // Force DEFLATED compression; paired with extractNativeLibs=true in Binary XML
                newEntry.setMethod(ZipEntry.DEFLATED);

                zos.putNextEntry(newEntry);
                zos.write(data);
                zos.closeEntry();
            }
        }
    }

    /**
     * Exact Binary XML (AXML) parser & patcher:
     * 1. Replaces exact package string in StringPool (without corrupting class names like com.pkg.MainActivity)
     * 2. Locates Resource IDs 0x010104ea (extractNativeLibs) and 0x01010591 (isSplitRequired)
     *    and modifies their binary boolean values in the XML tree.
     */
    private byte[] patchBinaryManifest(byte[] data, String oldPkg, String newPkg) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Replace exact null-terminated UTF-16LE and UTF-8 strings so class names aren't broken
        byte[] oldUtf16Exact = (oldPkg + "\0").getBytes(StandardCharsets.UTF_16LE);
        byte[] newUtf16Exact = (newPkg + "\0").getBytes(StandardCharsets.UTF_16LE);
        replaceBytesInPlace(data, oldUtf16Exact, newUtf16Exact);

        byte[] oldUtf8Exact = (oldPkg + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] newUtf8Exact = (newPkg + "\0").getBytes(StandardCharsets.UTF_8);
        replaceBytesInPlace(data, oldUtf8Exact, newUtf8Exact);

        // Also replace authorities/permissions prefixes if they use oldPkg + "." + permission
        replaceStringInBinaryXml(data, "com.android.vending.splits.required", "com.android.vending.splits.disabled");
        replaceStringInBinaryXml(data, "com.android.vending.splits", "com.android.vending.splitX");

        // 2. Parse AXML Chunks to patch Resource IDs for extractNativeLibs & isSplitRequired
        try {
            int fileSize = buf.getInt(4);
            int offset = 8;

            int extractNativeLibsIndex = -1;
            int isSplitRequiredIndex = -1;
            int requiredSplitTypesIndex = -1;
            int splitTypesIndex = -1;

            while (offset + 8 <= fileSize && offset + 8 <= data.length) {
                int chunkType = buf.getShort(offset) & 0xFFFF;
                int headerSize = buf.getShort(offset + 2) & 0xFFFF;
                int chunkSize = buf.getInt(offset + 4);
                if (chunkSize <= 0) break;

                // RES_XML_RESOURCE_MAP_TYPE = 0x0180
                if (chunkType == 0x0180) {
                    int count = (chunkSize - headerSize) / 4;
                    for (int i = 0; i < count; i++) {
                        int resId = buf.getInt(offset + headerSize + (i * 4));
                        if (resId == 0x010104ea) { // android:extractNativeLibs
                            extractNativeLibsIndex = i;
                        } else if (resId == 0x01010591) { // android:isSplitRequired
                            isSplitRequiredIndex = i;
                        } else if (resId == 0x0101064e) { // android:requiredSplitTypes
                            requiredSplitTypesIndex = i;
                        } else if (resId == 0x0101064f) { // android:splitTypes
                            splitTypesIndex = i;
                        }
                    }
                }
                // RES_XML_START_ELEMENT_TYPE = 0x0102
                else if (chunkType == 0x0102) {
                    int attrStart = buf.getShort(offset + 20) & 0xFFFF;
                    int attrSize = buf.getShort(offset + 22) & 0xFFFF;
                    int attrCount = buf.getShort(offset + 24) & 0xFFFF;
                    int attrsBase = offset + 16 + attrStart;

                    for (int i = 0; i < attrCount; i++) {
                        int attrOffset = attrsBase + (i * attrSize);
                        if (attrOffset + 20 > data.length) break;

                        int nameIdx = buf.getInt(attrOffset + 4);

                        // Force android:extractNativeLibs = true (0xFFFFFFFF)
                        if (nameIdx == extractNativeLibsIndex && extractNativeLibsIndex != -1) {
                            buf.putInt(attrOffset + 8, -1); // rawValue = -1
                            buf.putShort(attrOffset + 12, (short) 8); // size = 8
                            buf.put(attrOffset + 14, (byte) 0);
                            buf.put(attrOffset + 15, (byte) 0x12); // TYPE_INT_BOOLEAN
                            buf.putInt(attrOffset + 16, -1); // true
                        }
                        // Force android:isSplitRequired = false (0)
                        else if (nameIdx == isSplitRequiredIndex && isSplitRequiredIndex != -1) {
                            buf.putInt(attrOffset + 8, -1); // rawValue = -1
                            buf.putShort(attrOffset + 12, (short) 8);
                            buf.put(attrOffset + 14, (byte) 0);
                            buf.put(attrOffset + 15, (byte) 0x12); // TYPE_INT_BOOLEAN
                            buf.putInt(attrOffset + 16, 0); // false
                        }
                        // Neutralize requiredSplitTypes & splitTypes
                        else if ((nameIdx == requiredSplitTypesIndex && requiredSplitTypesIndex != -1) ||
                                 (nameIdx == splitTypesIndex && splitTypesIndex != -1)) {
                            buf.putInt(attrOffset + 4, -1); // Invalidate attribute name index
                        }
                    }
                }

                offset += chunkSize;
            }
        } catch (Exception ignored) {
        }

        return data;
    }

    private void replaceStringInBinaryXml(byte[] manifestBytes, String oldStr, String newStr) {
        if (oldStr.length() != newStr.length()) return;

        byte[] oldUtf16 = oldStr.getBytes(StandardCharsets.UTF_16LE);
        byte[] newUtf16 = newStr.getBytes(StandardCharsets.UTF_16LE);
        replaceBytesInPlace(manifestBytes, oldUtf16, newUtf16);

        byte[] oldUtf8 = oldStr.getBytes(StandardCharsets.UTF_8);
        byte[] newUtf8 = newStr.getBytes(StandardCharsets.UTF_8);
        replaceBytesInPlace(manifestBytes, oldUtf8, newUtf8);
    }

    private void replaceBytesInPlace(byte[] source, byte[] target, byte[] replacement) {
        if (target.length != replacement.length) return;
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
            }
        }
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int len;
        while ((len = is.read(buf)) > 0) bos.write(buf, 0, len);
        return bos.toByteArray();
    }

    private void promptInstallApk(File apkFile) {
        new AlertDialog.Builder(this)
                .setTitle("✅ Standalone Merged Clone Ready!")
                .setMessage("All Split APKs have been merged and native libraries unlocked:\n\n📄 " + apkFile.getName() + "\n\nOpen your File Manager -> Downloads and tap the file to install it!")
                .setPositiveButton("Got It", null)
                .show();
    }
}
