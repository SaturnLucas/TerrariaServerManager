package com.terrariaserver.manager;

/**
 * Scripts executados no Termux em segundo plano. Tudo e registrado em
 * Armazenamento/TerrariaServer/<id>/server.log, que o app mostra na tela.
 * O tmux roda no TERMUX e segura o login do proot-distro aberto (o proot-distro mata
 * todos os processos quando o login termina).
 */
public final class Scripts {
    private Scripts() {}

    private static String runInner(String inner) {
        return String.join("\n",
                "TASK=\".task-$$.sh\"",
                "proot-distro login debian $BIND -- bash -c \"cat > /root/$TASK\" <<'TS_INNER_EOF'",
                inner,
                "TS_INNER_EOF",
                "proot-distro login debian $BIND -- bash \"/root/$TASK\" || { echo '>> ERRO durante a instalacao (veja as linhas acima)'; exit 1; }",
                "proot-distro login debian -- rm -f \"/root/$TASK\"",
                "");
    }

    public static String start(Profile p) {
        String code = p.versionCode();
        String sd = p.dir();
        String name = "ts-" + p.id;
        String run = String.join("\n",
                "#!/bin/bash",
                "export TERM=xterm",
                "LOG=/ts/" + p.id + "/server.log",
                "cd /root/terraria/" + code + "/Linux",
                "mono --server --gc=sgen -O=all ./TerrariaServer.exe -config " + sd
                        + "/serverconfig.txt 2>&1 | sed -u 's/\\x1b\\[[0-9;?]*[a-zA-Z]//g' | tee -a \"$LOG\"");
        String inner = String.join("\n",
                "set -e",
                "export DEBIAN_FRONTEND=noninteractive TERM=xterm",
                "if ! command -v mono >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then",
                "  echo '>> [3/4] Instalando mono (so na primeira vez, 10 a 20 min, nao feche o app)...'",
                "  apt-get update -qq >/dev/null || true",
                "  apt-get install -y -qq mono-complete unzip curl ca-certificates >/dev/null",
                "fi",
                "CODE=" + code,
                "BASE=/root/terraria/$CODE",
                "if [ ! -f \"$BASE/Linux/TerrariaServer.exe\" ]; then",
                "  echo '>> [3/4] Baixando o servidor oficial do Terraria " + p.versionName() + "...'",
                "  mkdir -p /root/terraria && cd /root/terraria",
                "  curl -fsSL -o srv-$CODE.zip https://terraria.org/api/download/pc-dedicated-server/terraria-server-$CODE.zip",
                "  rm -rf tmp-$CODE && mkdir tmp-$CODE && unzip -q srv-$CODE.zip -d tmp-$CODE",
                "  rm -rf \"$BASE\" && mkdir -p \"$BASE\"",
                "  cp -r tmp-$CODE/*/. \"$BASE/\"",
                "  rm -rf tmp-$CODE srv-$CODE.zip",
                "  cd \"$BASE/Linux\" && rm -f System*.dll Mono*.dll monoconfig mscorlib.dll",
                "fi",
                "SD=" + sd,
                "mkdir -p \"$SD\" /ts/" + p.id + "/worlds",
                "touch /ts/" + p.id + "/banlist.txt",
                "cat > \"$SD/serverconfig.txt\" <<'TS_CFG_EOF'",
                p.config().trim(),
                "TS_CFG_EOF",
                "cat > \"$SD/run.sh\" <<'TS_RUN_EOF'",
                run,
                "TS_RUN_EOF",
                "chmod +x \"$SD/run.sh\"");
        return String.join("\n",
                "export DEBIAN_FRONTEND=noninteractive",
                "SHARED=/storage/emulated/0/TerrariaServer",
                "D=\"$SHARED/" + p.id + "\"",
                "mkdir -p \"$D/worlds\" \"$D/backups\" 2>/dev/null",
                "if [ ! -w \"$D\" ]; then echo 'Sem acesso ao armazenamento: rode termux-setup-storage no Termux' > \"$HOME/terraria-erro.txt\"; exit 1; fi",
                ": > \"$D/server.log\"",
                "exec >> \"$D/server.log\" 2>&1",
                "BIND=\"--bind $SHARED:/ts\"",
                "echo '>> [1/4] Preparando o Termux...'",
                "termux-wake-lock 2>/dev/null || true",
                "if ! { command -v proot-distro && command -v tmux && command -v curl; } >/dev/null 2>&1; then",
                "  pkg update -y >/dev/null 2>&1",
                "  pkg install -y proot-distro tmux curl >/dev/null 2>&1",
                "fi",
                "command -v proot-distro >/dev/null 2>&1 || { echo '>> ERRO: nao consegui instalar o proot-distro (sem internet?)'; exit 1; }",
                "echo '>> [2/4] Preparando o Debian (so na primeira vez)...'",
                "proot-distro login debian -- true >/dev/null 2>&1 || proot-distro install debian >/dev/null 2>&1",
                "proot-distro login debian -- true >/dev/null 2>&1 || { echo '>> ERRO: nao consegui instalar o Debian'; exit 1; }",
                runInner(inner),
                "echo '>> [4/4] Iniciando o servidor...'",
                "tmux kill-session -t " + name + " 2>/dev/null || true",
                "tmux new-session -d -s " + name + " \"proot-distro login debian $BIND -- bash " + sd
                        + "/run.sh\" \\; set-option -t " + name + " remain-on-exit on",
                "sleep 2",
                "if tmux has-session -t " + name + " 2>/dev/null; then",
                "  echo '>> Servidor em execucao. Quando aparecer Server started, ja aceita jogadores.'",
                "else",
                "  echo '>> ERRO: o servidor nao iniciou'",
                "fi",
                "");
    }

    private static String sendKeys(Profile p, String cmd) {
        String safe = cmd.replace("'", "'\\''");
        String n = "ts-" + p.id;
        return "tmux send-keys -t " + n + " -l '" + safe + "' && tmux send-keys -t " + n + " Enter";
    }

    public static String command(Profile p, String cmd) {
        return sendKeys(p, cmd) + "\n";
    }

    public static String backup(Profile p) {
        String d = "/storage/emulated/0/TerrariaServer/" + p.id;
        return sendKeys(p, "save") + " 2>/dev/null || true\nsleep 3\n"
                + "mkdir -p " + d + "/backups\n"
                + "tar czf " + d + "/backups/mundo-$(date +%Y%m%d-%H%M%S).tgz -C " + d + " worlds\n";
    }

    /** Comando unico para o usuario colar no Termux (libera o app e o armazenamento). */
    public static String enableExternalApps() {
        return "mkdir -p ~/.termux && grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null"
                + " || echo 'allow-external-apps=true' >> ~/.termux/termux.properties;"
                + " termux-reload-settings; termux-setup-storage";
    }
}
