package com.terrariaserver.manager;

/** Gera os scripts bash executados no Termux (Debian via proot-distro + mono). */
public final class Scripts {
    private Scripts() {}

    private static String prefix() {
        return String.join("\n",
                "export DEBIAN_FRONTEND=noninteractive",
                "SHARED=/storage/emulated/0/TerrariaServer",
                "mkdir -p \"$SHARED\" 2>/dev/null",
                "BIND=\"\"",
                "[ -w \"$SHARED\" ] && BIND=\"--bind $SHARED:/ts\"",
                "command -v proot-distro >/dev/null 2>&1 || { pkg update -y && pkg install -y proot-distro curl; }",
                "proot-distro login debian -- true >/dev/null 2>&1 || proot-distro install debian",
                "");
    }

    private static String runInner(String inner) {
        return String.join("\n",
                "TASK=\".task-$$.sh\"",
                "proot-distro login debian $BIND -- bash -c \"cat > /root/$TASK\" <<'TS_INNER_EOF'",
                inner,
                "TS_INNER_EOF",
                "proot-distro login debian $BIND -- bash \"/root/$TASK\"",
                "proot-distro login debian -- rm -f \"/root/$TASK\"",
                "");
    }

    /** Instala tudo que falta, grava a config e inicia o servidor numa sessao tmux. */
    public static String start(Profile p) {
        String code = p.versionCode();
        String sd = p.dir();
        String run = String.join("\n",
                "#!/bin/bash",
                "export TERM=xterm",
                "LOG=" + sd + "/server.log",
                "if [ -d /ts ]; then mkdir -p /ts/" + p.id + "; LOG=/ts/" + p.id + "/server.log; fi",
                "cd /root/terraria/" + code + "/Linux",
                "mono --server --gc=sgen -O=all ./TerrariaServer.exe -config " + sd
                        + "/serverconfig.txt 2>&1 | sed -u 's/\\x1b\\[[0-9;?]*[a-zA-Z]//g' | tee -a \"$LOG\"");
        String inner = String.join("\n",
                "set -e",
                "export DEBIAN_FRONTEND=noninteractive TERM=xterm",
                "if ! command -v mono >/dev/null 2>&1 || ! command -v tmux >/dev/null 2>&1; then",
                "  echo '>> Instalando mono e ferramentas (primeira vez, pode demorar)...'",
                "  apt-get update && apt-get install -y mono-complete tmux unzip curl ca-certificates",
                "fi",
                "CODE=" + code,
                "BASE=/root/terraria/$CODE",
                "if [ ! -f \"$BASE/Linux/TerrariaServer.exe\" ]; then",
                "  echo '>> Baixando servidor oficial do Terraria " + p.versionName() + "...'",
                "  mkdir -p /root/terraria && cd /root/terraria",
                "  curl -fL -o srv-$CODE.zip https://terraria.org/api/download/pc-dedicated-server/terraria-server-$CODE.zip",
                "  rm -rf tmp-$CODE && mkdir tmp-$CODE && unzip -q srv-$CODE.zip -d tmp-$CODE",
                "  rm -rf \"$BASE\" && mkdir -p \"$BASE\"",
                "  cp -r tmp-$CODE/*/. \"$BASE/\"",
                "  rm -rf tmp-$CODE srv-$CODE.zip",
                "  cd \"$BASE/Linux\" && rm -f System*.dll Mono*.dll monoconfig mscorlib.dll",
                "fi",
                "SD=" + sd,
                "mkdir -p \"$SD/worlds\"",
                "touch \"$SD/banlist.txt\"",
                "cat > \"$SD/serverconfig.txt\" <<'TS_CFG_EOF'",
                p.config().trim(),
                "TS_CFG_EOF",
                "cat > \"$SD/run.sh\" <<'TS_RUN_EOF'",
                run,
                "TS_RUN_EOF",
                "chmod +x \"$SD/run.sh\"",
                "tmux kill-session -t ts-" + p.id + " 2>/dev/null || true",
                "tmux new-session -d -s ts-" + p.id + " \"bash $SD/run.sh\"",
                "sleep 3",
                "if tmux has-session -t ts-" + p.id + " 2>/dev/null; then",
                "  echo '>> Servidor iniciado! Porta " + p.port + ". Volte ao app Terraria Server.'",
                "else",
                "  echo '>> Falha ao iniciar. Veja o log em ' $SD/server.log",
                "fi",
                "LOG=" + sd + "/server.log",
                "if [ -d /ts ]; then mkdir -p /ts/" + p.id + "; LOG=/ts/" + p.id + "/server.log; fi",
                "touch \"$LOG\"",
                "echo '>> Acompanhando o servidor ao vivo. Mundo grande pode levar varios minutos para gerar.'",
                "echo '>> O servidor so aceita jogadores depois de aparecer: Server started. Ctrl+C sai daqui e ele continua rodando.'",
                "tail -n 60 -f \"$LOG\"");
        return prefix()
                + "termux-wake-lock 2>/dev/null || true\n"
                + runInner(inner)
                + "echo\nsleep 6\n";
    }

    private static String sendKeys(Profile p, String cmd) {
        String safe = cmd.replace("'", "'\\''");
        return "tmux send-keys -t ts-" + p.id + " -l '" + safe + "' && tmux send-keys -t ts-" + p.id + " Enter";
    }

    public static String command(Profile p, String cmd) {
        return prefix() + runInner(sendKeys(p, cmd));
    }

    public static String backup(Profile p) {
        String inner = String.join("\n",
                "D=/root/backups/" + p.id,
                "if [ -d /ts ]; then D=/ts/" + p.id + "/backups; fi",
                "mkdir -p \"$D\"",
                sendKeys(p, "save") + " 2>/dev/null || true",
                "sleep 3",
                "tar czf \"$D/mundo-$(date +%Y%m%d-%H%M%S).tgz\" -C " + p.dir() + " worlds",
                "echo backup ok");
        return prefix() + runInner(inner);
    }

    /** Comando unico para o usuario colar no Termux caso o atalho automatico nao esteja liberado. */
    public static String enableExternalApps() {
        return "mkdir -p ~/.termux && grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null"
                + " || echo 'allow-external-apps=true' >> ~/.termux/termux.properties;"
                + " termux-reload-settings; termux-setup-storage";
    }
}
