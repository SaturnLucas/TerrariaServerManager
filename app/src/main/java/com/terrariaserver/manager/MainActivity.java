package com.terrariaserver.manager;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout serverList;
    private TextView tvEmpty;
    private TextView tvTermux;
    private MaterialButton btnTermuxInstall;
    private MaterialButton btnTermuxPerm;
    private TextView tvUpdate;
    private ProgressBar progress;
    private MaterialButton btnInstall;
    private MaterialButton btnUpdate;

    private String downloadUrl;
    private File apkFile;
    private boolean waitingInstallPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serverList = findViewById(R.id.serverList);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvTermux = findViewById(R.id.tvTermux);
        btnTermuxInstall = findViewById(R.id.btnTermuxInstall);
        btnTermuxPerm = findViewById(R.id.btnTermuxPerm);
        tvUpdate = findViewById(R.id.tvUpdate);
        progress = findViewById(R.id.progress);
        btnInstall = findViewById(R.id.btnInstall);
        btnUpdate = findViewById(R.id.btnUpdate);

        ((TextView) findViewById(R.id.tvVersion)).setText("Versão " + BuildConfig.VERSION_NAME);

        findViewById(R.id.btnNew).setOnClickListener(v ->
                startActivity(new Intent(this, ServerEditActivity.class)));
        btnTermuxInstall.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/termux/termux-app/releases"))));
        btnTermuxPerm.setOnClickListener(v -> ActivityCompat.requestPermissions(this,
                new String[]{TermuxBridge.PERMISSION}, 1));
        findViewById(R.id.btnTermuxCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Termux", Scripts.enableExternalApps()));
            Toast.makeText(this, "Copiado. Cole no Termux e dê Enter.", Toast.LENGTH_LONG).show();
        });
        btnUpdate.setOnClickListener(v -> checkUpdate(true));
        btnInstall.setOnClickListener(v -> downloadAndInstall());

        checkUpdate(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServers();
        refreshTermux();
        if (waitingInstallPermission && getPackageManager().canRequestPackageInstalls()) {
            waitingInstallPermission = false;
            if (apkFile != null && apkFile.exists()) {
                installApk();
            } else if (downloadUrl != null) {
                downloadAndInstall();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        refreshTermux();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void refreshTermux() {
        boolean installed = TermuxBridge.installed(this);
        boolean permitted = TermuxBridge.permitted(this);
        if (!installed) {
            tvTermux.setText("O servidor roda dentro do Termux (Android não executa o servidor do Terraria direto). "
                    + "Instale o Termux pelo GitHub ou F-Droid (a versão da Play Store está desatualizada).");
            btnTermuxInstall.setVisibility(View.VISIBLE);
            btnTermuxPerm.setVisibility(View.GONE);
        } else if (!permitted) {
            tvTermux.setText("Termux instalado. Falta liberar o controle pelo app: toque no botão abaixo e aceite. "
                    + "Depois toque em \"Copiar comando de preparação\", cole no Termux e dê Enter (uma vez só).");
            btnTermuxInstall.setVisibility(View.GONE);
            btnTermuxPerm.setVisibility(View.VISIBLE);
        } else {
            tvTermux.setText("Termux pronto. Se o botão Iniciar não abrir o Termux, rode uma vez o comando de "
                    + "preparação abaixo. Na primeira vez o app instala Debian + mono (download grande, use Wi‑Fi).");
            btnTermuxInstall.setVisibility(View.GONE);
            btnTermuxPerm.setVisibility(View.GONE);
        }
    }

    private void refreshServers() {
        serverList.removeAllViews();
        List<Profile> list = ProfileStore.load(this);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        for (Profile p : list) {
            com.google.android.material.card.MaterialCardView card =
                    new com.google.android.material.card.MaterialCardView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            card.setLayoutParams(lp);
            card.setRadius(dp(18));
            card.setCardElevation(0);
            card.setCardBackgroundColor(0xFF1B2E22);
            card.setStrokeColor(0xFF2F5238);
            card.setStrokeWidth(dp(1));
            LinearLayout in = new LinearLayout(this);
            in.setOrientation(LinearLayout.VERTICAL);
            in.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView t = new TextView(this);
            t.setText(p.name);
            t.setTextColor(0xFFE6F3E8);
            t.setTextSize(17);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView s = new TextView(this);
            s.setText("Terraria " + p.versionName() + " • mundo " + p.worldFile() + " • porta " + p.port);
            s.setTextColor(0xFF9DB5A2);
            s.setTextSize(13);
            in.addView(t);
            in.addView(s);
            card.addView(in);
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, ServerDetailActivity.class);
                i.putExtra("id", p.id);
                startActivity(i);
            });
            serverList.addView(card);
        }
    }

    // ---------- Atualizacoes ----------

    private int parseBuild(String tag) {
        try {
            return Integer.parseInt(tag.substring(tag.lastIndexOf('.') + 1).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private void checkUpdate(boolean manual) {
        tvUpdate.setText("Buscando atualização...");
        btnUpdate.setEnabled(false);
        io.execute(() -> {
            String msg;
            boolean hasNew = false;
            String url = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                        "https://api.github.com/repos/" + BuildConfig.REPO_SLUG + "/releases/latest")
                        .openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                int code = c.getResponseCode();
                if (code != 200) {
                    throw new Exception("HTTP " + code);
                }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                    }
                }
                JSONObject j = new JSONObject(bo.toString("UTF-8"));
                String tag = j.getString("tag_name");
                int remote = parseBuild(tag);
                JSONArray assets = j.optJSONArray("assets");
                if (assets != null) {
                    for (int k = 0; k < assets.length(); k++) {
                        JSONObject a = assets.getJSONObject(k);
                        if (a.getString("name").endsWith(".apk")) {
                            url = a.getString("browser_download_url");
                            break;
                        }
                    }
                }
                if (remote > BuildConfig.VERSION_CODE && url != null) {
                    hasNew = true;
                    msg = "Nova versão disponível: " + tag.replace("v", "")
                            + "\nVocê está na " + BuildConfig.VERSION_NAME;
                } else {
                    msg = "Você está na versão mais recente (" + BuildConfig.VERSION_NAME + ").";
                }
            } catch (Exception e) {
                msg = manual ? "Não foi possível verificar agora (" + e.getMessage() + ")."
                        : "Toque em verificar para buscar novas versões.";
            }
            final String fm = msg;
            final boolean fn = hasNew;
            final String fu = url;
            ui.post(() -> {
                tvUpdate.setText(fm);
                btnUpdate.setEnabled(true);
                if (fn) {
                    downloadUrl = fu;
                    btnInstall.setVisibility(View.VISIBLE);
                } else {
                    btnInstall.setVisibility(View.GONE);
                }
            });
        });
    }

    private void downloadAndInstall() {
        if (downloadUrl == null) {
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            waitingInstallPermission = true;
            Toast.makeText(this, "Permita instalar apps deste app e volte", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        btnInstall.setEnabled(false);
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);
        tvUpdate.setText("Baixando atualização... 0%");
        final String urlStr = downloadUrl;
        io.execute(() -> {
            String error = null;
            try {
                File dir = new File(getCacheDir(), "updates");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File out = new File(dir, "update.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) {
                    throw new Exception("HTTP " + c.getResponseCode());
                }
                long total = c.getContentLengthLong();
                long done = 0;
                int last = -1;
                try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            final int pct = (int) (done * 100 / total);
                            if (pct != last) {
                                last = pct;
                                ui.post(() -> {
                                    progress.setProgress(pct);
                                    tvUpdate.setText("Baixando atualização... " + pct + "%");
                                });
                            }
                        }
                    }
                }
                apkFile = out;
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            ui.post(() -> {
                btnInstall.setEnabled(true);
                progress.setVisibility(View.GONE);
                if (err != null) {
                    tvUpdate.setText("Falha no download: " + err);
                } else {
                    tvUpdate.setText("Download concluído. Abrindo instalação...");
                    installApk();
                }
            });
        });
    }

    private void installApk() {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            tvUpdate.setText("Não foi possível abrir a instalação: " + e.getMessage());
        }
    }
}
