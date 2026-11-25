package com.terrariaserver.manager;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "prefs";
    private static final String KEY_SERVER = "server";
    private static final String KEY_PANEL = "panel";

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextInputEditText etServer;
    private TextInputEditText etPanel;
    private TextView tvStatus;
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

        etServer = findViewById(R.id.etServer);
        etPanel = findViewById(R.id.etPanel);
        tvStatus = findViewById(R.id.tvStatus);
        tvUpdate = findViewById(R.id.tvUpdate);
        progress = findViewById(R.id.progress);
        btnInstall = findViewById(R.id.btnInstall);
        btnUpdate = findViewById(R.id.btnUpdate);

        ((TextView) findViewById(R.id.tvVersion)).setText("Versão " + BuildConfig.VERSION_NAME);

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        etServer.setText(sp.getString(KEY_SERVER, ""));
        etPanel.setText(sp.getString(KEY_PANEL, ""));

        findViewById(R.id.btnCheck).setOnClickListener(v -> checkServer());
        findViewById(R.id.btnCopy).setOnClickListener(v -> copyIp());
        findViewById(R.id.btnPanel).setOnClickListener(v -> openPanel());
        btnUpdate.setOnClickListener(v -> checkUpdate(true));
        btnInstall.setOnClickListener(v -> downloadAndInstall());

        checkUpdate(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingInstallPermission && getPackageManager().canRequestPackageInstalls()) {
            waitingInstallPermission = false;
            if (apkFile != null && apkFile.exists()) {
                installApk();
            } else if (downloadUrl != null) {
                downloadAndInstall();
            }
        }
    }

    private String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_SERVER, text(etServer))
                .putString(KEY_PANEL, text(etPanel))
                .apply();
    }

    // ---------- Servidor ----------

    private void checkServer() {
        save();
        String s = text(etServer);
        String h = s;
        int p = 7777;
        int idx = s.lastIndexOf(':');
        if (idx > 0) {
            h = s.substring(0, idx).trim();
            try {
                p = Integer.parseInt(s.substring(idx + 1).trim());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Porta inválida", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (h.isEmpty()) {
            Toast.makeText(this, "Digite o IP do servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        final String host = h;
        final int port = p;
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_dim));
        tvStatus.setText("Verificando...");
        io.execute(() -> {
            boolean ok = false;
            try (Socket so = new Socket()) {
                so.connect(new InetSocketAddress(host, port), 4000);
                ok = true;
            } catch (Exception ignored) {
            }
            final boolean result = ok;
            ui.post(() -> {
                tvStatus.setText(result ? "● Online" : "● Offline");
                tvStatus.setTextColor(ContextCompat.getColor(this,
                        result ? R.color.online : R.color.offline));
            });
        });
    }

    private void copyIp() {
        save();
        String s = text(etServer);
        if (s.isEmpty()) {
            Toast.makeText(this, "Nada para copiar", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("IP do servidor", s));
        Toast.makeText(this, "IP copiado", Toast.LENGTH_SHORT).show();
    }

    // ---------- Painel ----------

    private void openPanel() {
        save();
        String url = text(etPanel);
        if (url.isEmpty()) {
            Toast.makeText(this, "Cole o endereço do painel", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        Intent i = new Intent(this, PanelActivity.class);
        i.putExtra("url", url);
        startActivity(i);
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
        progress.setIndeterminate(false);
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
