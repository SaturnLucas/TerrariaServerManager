package com.terrariaserver.manager;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerDetailActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Profile p;
    private TextView tvStatus, tvLog, tvAddress, tvSummary, tvName;
    private TextInputEditText etCmd;
    private boolean active = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!active) {
                return;
            }
            checkStatus();
            readLog();
            ui.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        p = ProfileStore.get(this, getIntent().getStringExtra("id"));
        if (p == null) {
            finish();
            return;
        }
        tvName = findViewById(R.id.tvName);
        tvSummary = findViewById(R.id.tvSummary);
        tvStatus = findViewById(R.id.tvStatus);
        tvAddress = findViewById(R.id.tvAddress);
        tvLog = findViewById(R.id.tvLog);
        etCmd = findViewById(R.id.etCmd);

        findViewById(R.id.btnStart).setOnClickListener(v -> {
            if (!ready()) {
                return;
            }
            if (!getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("termux_prepped", false)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Preparar o Termux (uma vez)")
                        .setMessage("O Termux só aceita comandos do app depois de rodar um comando de preparação.\n\n"
                                + "1. Toque em \"Copiar e abrir Termux\".\n"
                                + "2. No Termux, cole (segure na tela > Colar) e dê Enter. Aceite a permissão de armazenamento.\n"
                                + "3. Volte ao app e toque em \"Já fiz\".")
                        .setPositiveButton("Copiar e abrir Termux", (d, w) -> {
                            copy("Termux", Scripts.enableExternalApps());
                            Intent li = getPackageManager().getLaunchIntentForPackage("com.termux");
                            if (li != null) {
                                startActivity(li);
                            }
                        })
                        .setNeutralButton("Já fiz", (d, w) -> {
                            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                                    .putBoolean("termux_prepped", true).apply();
                            TermuxBridge.run(this, Scripts.start(p), true);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return;
            }
            TermuxBridge.run(this, Scripts.start(p), true);
            Toast.makeText(this, "Abrindo o Termux para iniciar...", Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.btnStop).setOnClickListener(v -> send("exit"));
        findViewById(R.id.btnSave).setOnClickListener(v -> send("save"));
        findViewById(R.id.btnBackup).setOnClickListener(v -> {
            if (ready()) {
                TermuxBridge.run(this, Scripts.backup(p), false);
                Toast.makeText(this,
                        "Backup em Armazenamento/TerrariaServer/" + p.id + "/backups", Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.btnCopyIp).setOnClickListener(v -> copy("IP", ipPort()));
        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String c = etCmd.getText() == null ? "" : etCmd.getText().toString().trim();
            if (!c.isEmpty()) {
                send(c);
                etCmd.setText("");
            }
        });
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            Intent i = new Intent(this, ServerEditActivity.class);
            i.putExtra("id", p.id);
            startActivity(i);
        });
        findViewById(R.id.btnScript).setOnClickListener(v -> {
            copy("Script", Scripts.start(p));
            Toast.makeText(this, "Script copiado. Cole no Termux e dê Enter.", Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.btnDelete).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Excluir servidor?")
                        .setMessage("Remove só este perfil do app. Os arquivos do mundo ficam no Termux.")
                        .setPositiveButton("Excluir", (d, w) -> {
                            ProfileStore.delete(this, p.id);
                            finish();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show());

        LinearLayout quick = findViewById(R.id.quickCmds);
        String[] cmds = {"playing", "save", "time", "dawn", "noon", "dusk", "midnight", "settle", "version", "password"};
        for (String c : cmds) {
            MaterialButton b = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(c);
            b.setAllCaps(false);
            b.setOnClickListener(v -> send(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) (8 * getResources().getDisplayMetrics().density);
            quick.addView(b, lp);
        }

        tvLog.setOnClickListener(v -> askStorage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Profile fresh = ProfileStore.get(this, p.id);
        if (fresh != null) {
            p = fresh;
        }
        tvName.setText(p.name);
        tvSummary.setText("Terraria " + p.versionName() + " • Mundo " + p.worldFile() + " ("
                + Catalog.SIZES[p.size] + ") • " + Catalog.DIFFICULTIES[p.difficulty]
                + " • até " + p.maxPlayers + " jogadores");
        String lan = localIp();
        tvAddress.setText("Mesma rede Wi‑Fi: " + (lan == null ? "sem rede" : lan + ":" + p.port)
                + "\nFora de casa: precisa liberar a porta " + p.port + " no roteador ou usar um túnel/VPN.");
        active = true;
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
        ui.removeCallbacks(ticker);
    }

    private String ipPort() {
        String lan = localIp();
        return (lan == null ? "127.0.0.1" : lan) + ":" + p.port;
    }

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
    }

    private boolean ready() {
        if (!TermuxBridge.installed(this)) {
            Toast.makeText(this, "Instale o Termux primeiro (tela inicial do app)", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!TermuxBridge.permitted(this)) {
            ActivityCompat.requestPermissions(this, new String[]{TermuxBridge.PERMISSION}, 1);
            return false;
        }
        return true;
    }

    private void send(String cmd) {
        if (ready()) {
            TermuxBridge.run(this, Scripts.command(p, cmd), false);
            Toast.makeText(this, "Comando enviado: " + cmd, Toast.LENGTH_SHORT).show();
        }
    }

    private static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void checkStatus() {
        final int port = p.port;
        io.execute(() -> {
            boolean ok = false;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 1500);
                ok = true;
            } catch (Exception ignored) {
            }
            final boolean r = ok;
            ui.post(() -> {
                tvStatus.setText(r ? "● Online" : "● Offline");
                tvStatus.setTextColor(ContextCompat.getColor(this, r ? R.color.online : R.color.offline));
            });
        });
    }

    private boolean hasStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void askStorage() {
        if (hasStorage()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
        }
    }

    private void readLog() {
        if (!hasStorage()) {
            tvLog.setText("Toque aqui e permita \"acesso a todos os arquivos\" para ver o console do servidor.");
            return;
        }
        final File f = new File("/storage/emulated/0/TerrariaServer/" + p.id + "/server.log");
        io.execute(() -> {
            String t;
            try {
                if (!f.exists()) {
                    t = "O console aparece aqui assim que o servidor iniciar.";
                } else {
                    try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                        long len = r.length();
                        long start = Math.max(0, len - 5000);
                        r.seek(start);
                        byte[] b = new byte[(int) (len - start)];
                        r.readFully(b);
                        t = new String(b, "UTF-8");
                    }
                }
            } catch (Exception e) {
                t = "Não foi possível ler o log: " + e.getMessage();
            }
            final String fin = t;
            ui.post(() -> tvLog.setText(fin));
        });
    }
}
