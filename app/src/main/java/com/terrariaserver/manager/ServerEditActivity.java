package com.terrariaserver.manager;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;

public class ServerEditActivity extends AppCompatActivity {

    private LinearLayout root;
    private Profile p;

    private TextInputEditText etName, etWorld, etSeed, etMax, etPort, etPass, etMotd;
    private MaterialAutoCompleteTextView ddVersion, ddSize, ddDiff, ddSeed, ddLang, ddJourney;
    private MaterialSwitch swSecure, swUpnp, swNpc;
    private TextInputLayout tilSeed, tilJourney;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String id = getIntent().getStringExtra("id");
        Profile existing = id == null ? null : ProfileStore.get(this, id);
        p = existing != null ? existing : new Profile();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0E1712);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        sv.addView(root);
        setContentView(sv);

        title(existing == null ? "Novo servidor" : "Editar servidor", 24, 0xFFE6F3E8);

        section("Servidor");
        etName = text("Nome do servidor", p.name, InputType.TYPE_CLASS_TEXT);
        ddVersion = dropdown("Versão do Terraria", Catalog.VERSION_LABELS, p.version);
        note("Use a MESMA versão do Terraria do seu celular para conseguir entrar. A 1.4.6 não funciona em celular (falta uma biblioteca que só existe para PC). Se trocar a versão de um mundo já criado, mude também o nome do mundo: versão antiga não abre mundo de versão nova.");

        section("Mundo");
        etWorld = text("Nome do mundo", p.world, InputType.TYPE_CLASS_TEXT);
        ddSize = dropdown("Tamanho", Catalog.SIZES, p.size);
        ddDiff = dropdown("Dificuldade", Catalog.DIFFICULTIES, p.difficulty);
        ddSeed = dropdown("Seed / tipo de mundo", Catalog.SEED_LABELS, p.seedPreset);
        etSeed = text("Seed personalizada", p.seedCustom, InputType.TYPE_CLASS_TEXT);
        tilSeed = (TextInputLayout) etSeed.getParent().getParent();
        ddJourney = dropdown("Poderes do modo Jornada", Catalog.JOURNEY_LABELS, p.journey);
        tilJourney = (TextInputLayout) ddJourney.getParent().getParent();
        note("Tamanho, dificuldade e seed só valem ao CRIAR o mundo. Para gerar outro mundo, mude o nome dele. "
                + "Corruption ou Crimson é sorteado pela seed (use a seed Drunk para ter os dois).");

        section("Jogadores e rede");
        etMax = text("Máximo de jogadores (1 a 255)", String.valueOf(p.maxPlayers), InputType.TYPE_CLASS_NUMBER);
        etPort = text("Porta", String.valueOf(p.port), InputType.TYPE_CLASS_NUMBER);
        etPass = text("Senha (vazio = sem senha)", p.password, InputType.TYPE_CLASS_TEXT);
        etMotd = text("Mensagem do dia (MOTD)", p.motd, InputType.TYPE_CLASS_TEXT);
        ddLang = dropdown("Idioma do servidor", Catalog.LANG_LABELS, p.language);
        swSecure = toggle("Proteção anti-cheat (secure)", p.secure);
        swNpc = toggle("Inimigos mais suaves (npcstream, usa mais internet)", p.smoothNpc);
        swUpnp = toggle("Abrir porta automaticamente (UPnP)", p.upnp);

        MaterialButton save = new MaterialButton(this, null, 0);
        save.setText("Salvar servidor");
        styleButton(save, true);
        save.setOnClickListener(v -> save());
        root.addView(save, lpTop(20));

        ddSeed.setOnItemClickListener((a, v, pos, idl) -> updateVisibility());
        ddDiff.setOnItemClickListener((a, v, pos, idl) -> updateVisibility());
        updateVisibility();
    }

    private void updateVisibility() {
        tilSeed.setVisibility(index(ddSeed, Catalog.SEED_LABELS) == Catalog.SEED_CUSTOM
                ? View.VISIBLE : View.GONE);
        tilJourney.setVisibility(index(ddDiff, Catalog.DIFFICULTIES) == 3 ? View.VISIBLE : View.GONE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lpTop(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        return lp;
    }

    private void title(String t, int size, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(v, lpTop(8));
    }

    private void section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(18);
        v.setTextColor(0xFF5FD068);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(v, lpTop(26));
    }

    private void note(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTextColor(0xFF9DB5A2);
        v.setLineSpacing(dp(2), 1f);
        root.addView(v, lpTop(8));
    }

    private void styleButton(MaterialButton b, boolean filled) {
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setMinHeight(dp(56));
        b.setCornerRadius(dp(16));
        if (filled) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF5FD068));
            b.setTextColor(0xFF07200C);
        }
    }

    private TextInputEditText text(String hint, String value, int inputType) {
        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        til.setBoxCornerRadii(dp(16), dp(16), dp(16), dp(16));
        TextInputEditText e = new TextInputEditText(til.getContext());
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setText(value);
        til.addView(e);
        root.addView(til, lpTop(12));
        return e;
    }

    private MaterialAutoCompleteTextView dropdown(String hint, String[] items, int sel) {
        TextInputLayout til = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        til.setHint(hint);
        til.setBoxCornerRadii(dp(16), dp(16), dp(16), dp(16));
        MaterialAutoCompleteTextView d = new MaterialAutoCompleteTextView(til.getContext());
        d.setInputType(InputType.TYPE_NULL);
        d.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items));
        d.setText(items[Math.max(0, Math.min(items.length - 1, sel))], false);
        til.addView(d);
        root.addView(til, lpTop(12));
        return d;
    }

    private MaterialSwitch toggle(String label, boolean on) {
        MaterialSwitch s = new MaterialSwitch(this);
        s.setText(label);
        s.setTextColor(0xFFE6F3E8);
        s.setChecked(on);
        root.addView(s, lpTop(12));
        return s;
    }

    private int index(MaterialAutoCompleteTextView d, String[] items) {
        int i = Arrays.asList(items).indexOf(d.getText().toString());
        return Math.max(i, 0);
    }

    private String str(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private int num(TextInputEditText e, int def) {
        try {
            return Integer.parseInt(str(e));
        } catch (Exception x) {
            return def;
        }
    }

    private void save() {
        String name = str(etName);
        if (name.isEmpty()) {
            Toast.makeText(this, "Dê um nome ao servidor", Toast.LENGTH_SHORT).show();
            return;
        }
        int port = num(etPort, 7777);
        if (port < 1024 || port > 65535) {
            Toast.makeText(this, "Porta deve ficar entre 1024 e 65535", Toast.LENGTH_SHORT).show();
            return;
        }
        int max = num(etMax, 8);
        if (max < 1 || max > 255) {
            Toast.makeText(this, "Jogadores: de 1 a 255", Toast.LENGTH_SHORT).show();
            return;
        }
        p.name = name;
        p.version = index(ddVersion, Catalog.VERSION_LABELS);
        p.world = str(etWorld).isEmpty() ? "Mundo" : str(etWorld);
        p.size = index(ddSize, Catalog.SIZES);
        p.difficulty = index(ddDiff, Catalog.DIFFICULTIES);
        p.seedPreset = index(ddSeed, Catalog.SEED_LABELS);
        p.seedCustom = str(etSeed);
        p.journey = index(ddJourney, Catalog.JOURNEY_LABELS);
        p.maxPlayers = max;
        p.port = port;
        p.password = str(etPass);
        p.motd = str(etMotd);
        p.language = index(ddLang, Catalog.LANG_LABELS);
        p.secure = swSecure.isChecked();
        p.smoothNpc = swNpc.isChecked();
        p.upnp = swUpnp.isChecked();
        ProfileStore.upsert(this, p);
        Toast.makeText(this, "Servidor salvo", Toast.LENGTH_SHORT).show();
        finish();
    }
}
