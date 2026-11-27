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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerDetailActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Profile p;
    private TextView tvStatus, tvLog, tvAddress, tvSummary, tvName, tvStep, tvWorld;
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
            refreshWorld();
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
        tvStep = findViewById(R.id.tvStep);
        tvWorld = findViewById(R.id.tvWorld);
        tvAddress = findViewById(R.id.tvAddress);
        tvLog = findViewById(R.id.tvLog);
        etCmd = findViewById(R.id.etCmd);

        findViewById(R.id.btnStart).setOnClickListener(v -> startServer());
        findViewById(R.id.btnChooseWorld).setOnClickListener(v -> chooseWorld());
        findViewById(R.id.btnCreateWorld).setOnClickListener(v -> createWorldDialog());
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
                        .setMessage("Remove só este perfil do app. Os mundos continuam em Armazenamento/TerrariaServer.")
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
            lp.rightMargin = dp(8);
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
        updateTexts();
        active = true;
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
        ui.removeCallbacks(ticker);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void updateTexts() {
        tvName.setText(p.name);
        tvSummary.setText("Terraria " + p.versionName() + " • até " + p.maxPlayers + " jogadores • porta " + p.port);
        String lan = localIp();
        tvAddress.setText("Mesma rede Wi‑Fi: " + (lan == null ? "sem rede" : lan + ":" + p.port)
                + "\nFora de casa: precisa liberar a porta " + p.port + " no roteador ou usar um túnel.");
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

    // ---------- Iniciar ----------

    private void startServer() {
        if (!ready()) {
            return;
        }
        if (!hasStorage()) {
            askStorage();
            Toast.makeText(this, "Permita \"acesso a todos os arquivos\", volte e toque em Iniciar de novo",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("termux_prepped", false)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Preparar o Termux (uma vez)")
                    .setMessage("O Termux só aceita comandos do app depois de rodar um comando de preparação.\n\n"
                            + "1. Toque em \"Copiar e abrir Termux\".\n"
                            + "2. No Termux, cole (segure na tela > Colar) e dê Enter. Aceite a permissão de armazenamento.\n"
                            + "3. Volte ao app e toque em \"Já fiz\". Depois disso você não precisa mais olhar o Termux.")
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
                        startServer();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        TermuxBridge.run(this, Scripts.start(p), false);
        tvStep.setText(">> Comando enviado. O progresso aparece abaixo.");
        Toast.makeText(this, "Iniciando... acompanhe o progresso aqui no app", Toast.LENGTH_LONG).show();
    }

    // ---------- Mundos ----------

    private File worldsDir() {
        return new File("/storage/emulated/0/TerrariaServer/" + p.id + "/worlds");
    }

    private List<File> listWorlds() {
        List<File> out = new ArrayList<>();
        try {
            File[] fs = worldsDir().listFiles();
            if (fs != null) {
                Arrays.sort(fs);
                for (File f : fs) {
                    if (f.getName().endsWith(".wld")) {
                        out.add(f);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void refreshWorld() {
        String active = p.worldFile();
        boolean exists = false;
        for (File f : listWorlds()) {
            if (f.getName().equals(active + ".wld")) {
                exists = true;
            }
        }
        tvWorld.setText("Mundo ativo: " + active + (exists
                ? "\nPronto para jogar."
                : "\nAinda não criado: será gerado ao iniciar ("
                + Catalog.SIZES[p.size] + ", " + Catalog.DIFFICULTIES[p.difficulty] + ")."));
    }

    private void chooseWorld() {
        List<File> ws = listWorlds();
        if (ws.isEmpty()) {
            Toast.makeText(this, hasStorage() ? "Nenhum mundo criado ainda. Toque em Criar novo mundo."
                    : "Permita acesso a todos os arquivos para listar os mundos.", Toast.LENGTH_LONG).show();
            if (!hasStorage()) {
                askStorage();
            }
            return;
        }
        final String[] names = new String[ws.size()];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = ws.get(i).getName().replace(".wld", "");
            if (names[i].equals(p.worldFile())) {
                checked = i;
            }
        }
        final int[] sel = {checked};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Escolher mundo")
                .setSingleChoiceItems(names, checked, (d, w) -> sel[0] = w)
                .setPositiveButton("Usar e iniciar", (d, w) -> {
                    p.world = names[sel[0]];
                    ProfileStore.upsert(this, p);
                    refreshWorld();
                    startServer();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private TextInputEditText dlgField(LinearLayout box, String hint) {
        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        TextInputEditText e = new TextInputEditText(til.getContext());
        e.setSingleLine(true);
        til.addView(e);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.addView(til, lp);
        return e;
    }

    private Spinner dlgSpinner(LinearLayout box, String label, String[] items, int sel) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xFF9DB5A2);
        l.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        box.addView(l, lp);
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        s.setSelection(sel);
        box.addView(s);
        return s;
    }

    private void createWorldDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(10), dp(22), dp(0));
        TextInputEditText etName = dlgField(box, "Nome do mundo");
        Spinner spSize = dlgSpinner(box, "Tamanho", Catalog.SIZES, 1);
        Spinner spDiff = dlgSpinner(box, "Dificuldade", Catalog.DIFFICULTIES, 0);
        Spinner spSeed = dlgSpinner(box, "Seed / tipo de mundo", Catalog.SEED_LABELS, 0);
        TextInputEditText etSeed = dlgField(box, "Seed personalizada");
        ((View) etSeed.getParent().getParent()).setVisibility(View.GONE);
        spSeed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                ((View) etSeed.getParent().getParent()).setVisibility(
                        pos == Catalog.SEED_CUSTOM ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(this)
                .setTitle("Criar novo mundo")
                .setView(box)
                .setPositiveButton("Criar e iniciar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Dê um nome ao mundo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String file = name.replaceAll("[^A-Za-z0-9_-]", "_");
                    for (File f : listWorlds()) {
                        if (f.getName().equals(file + ".wld")) {
                            Toast.makeText(this, "Já existe um mundo com esse nome", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    p.world = name;
                    p.size = spSize.getSelectedItemPosition();
                    p.difficulty = spDiff.getSelectedItemPosition();
                    p.seedPreset = spSeed.getSelectedItemPosition();
                    p.seedCustom = etSeed.getText() == null ? "" : etSeed.getText().toString().trim();
                    ProfileStore.upsert(this, p);
                    dlg.dismiss();
                    refreshWorld();
                    startServer();
                }));
        dlg.show();
    }

    // ---------- Status e console ----------

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

    /**
     * NUNCA abrir conexao na porta do servidor: o servidor do Terraria (mono) cai quando uma
     * conexao fecha sem enviar dados. O status vem do arquivo .alive que o proprio servidor atualiza.
     */
    private void checkStatus() {
        if (!hasStorage()) {
            tvStatus.setText("● Sem acesso aos arquivos");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warn));
            return;
        }
        final File f = new File("/storage/emulated/0/TerrariaServer/" + p.id + "/.alive");
        io.execute(() -> {
            int state = 0; // 0 offline, 1 iniciando, 2 online
            try {
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < 15000) {
                    try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                        byte[] b = new byte[(int) Math.min(16, r.length())];
                        r.readFully(b);
                        state = new String(b, "UTF-8").trim().equals("ready") ? 2 : 1;
                    }
                }
            } catch (Exception ignored) {
            }
            final int st = state;
            ui.post(() -> {
                if (st == 2) {
                    tvStatus.setText("● Online");
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.online));
                } else if (st == 1) {
                    tvStatus.setText("● Iniciando (gerando mundo)...");
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warn));
                } else {
                    tvStatus.setText("● Offline");
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.offline));
                }
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
            tvLog.setText("Toque aqui e permita \"acesso a todos os arquivos\" para ver o progresso e o console.");
            return;
        }
        final File f = new File("/storage/emulated/0/TerrariaServer/" + p.id + "/server.log");
        io.execute(() -> {
            String t;
            String step = null;
            try {
                if (!f.exists()) {
                    t = "O progresso e o console aparecem aqui depois que você tocar em Iniciar.";
                } else {
                    try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                        long len = r.length();
                        long start = Math.max(0, len - 6000);
                        r.seek(start);
                        byte[] b = new byte[(int) (len - start)];
                        r.readFully(b);
                        String raw = new String(b, "UTF-8").replace("\r", "\n");
                        String[] lines = raw.split("\n");
                        StringBuilder sb = new StringBuilder();
                        int from = Math.max(0, lines.length - 40);
                        for (int i = 0; i < lines.length; i++) {
                            if (lines[i].startsWith(">>")) {
                                step = lines[i];
                            }
                            if (i >= from) {
                                sb.append(lines[i]).append("\n");
                            }
                        }
                        t = sb.toString();
                    }
                }
            } catch (Exception e) {
                t = "Não foi possível ler o log: " + e.getMessage();
            }
            final String fin = t;
            final String fs = step;
            ui.post(() -> {
                tvLog.setText(fin);
                if (fs != null) {
                    tvStep.setText(fs);
                }
            });
        });
    }
}
