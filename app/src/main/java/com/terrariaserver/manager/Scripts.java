package com.terrariaserver.manager;

/**
 * Scripts executados no Termux.
 * O tmux roda no TERMUX e segura o login do proot-distro aberto: o proot-distro mata
 * todos os processos quando o login termina, entao o servidor precisa viver dentro dele.
 */
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
                "command -v tmux >/dev/null 2>&1 || pkg install -y tmux",
                "proot-distro login debian -- true >/dev/null 2>&1 || proot-distro install debian",
                "");
    }

    /** Executa um script dentro do Debian (envia o arquivo pelo proprio proot). */
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

    /** Instala o que falta, grava a config e inicia o servidor dentro de um tmux do Termux. */
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
                "if ! command -v mono >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then",
                "  echo '>> Instalando mono e ferramentas (primeira vez, pode demorar)...'",
                "  apt-get update && apt-get install -y mono-complete unzip curl ca-certificates",
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
                "echo '>> Preparo concluido.'");
        String name = "ts-" + p.id;
        return prefix()
                + "termux-wake-lock 2>/dev/null || true\n"
                + runInner(inner)
                + String.join("\n",
                "tmux kill-session -t " + name + " 2>/dev/null || true",
                "tmux new-session -d -s " + name + " \"proot-distro login debian $BIND -- bash " + sd
                        + "/run.sh\" \\; set-option -t " + name + " remain-on-exit on",
                "echo",
                "echo '>> Servidor iniciando. Console ao vivo abaixo.'",
                "echo '>> Mundo grande leva varios minutos para gerar. Aceita jogadores quando aparecer: Server started.'",
                "echo '>> Pode sair do Termux (botao inicio): o servidor continua rodando.'",
                "sleep 2",
                "tmux attach -t " + name,
                "echo",
                "echo '>> Saiu do console.'",
                "sleep 5",
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
        String inner = String.join("\n",
                "D=/root/backups/" + p.id,
                "if [ -d /ts ]; then D=/ts/" + p.id + "/backups; fi",
                "mkdir -p \"$D\"",
                "tar czf \"$D/mundo-$(date +%Y%m%d-%H%M%S).tgz\" -C " + p.dir() + " worlds");
        return sendKeys(p, "save") + " 2>/dev/null || true\nsleep 3\n" + prefix() + runInner(inner);
    }

    /** Comando unico para o usuario colar no Termux (libera o app e o armazenamento). */
    public static String enableExternalApps() {
        return "mkdir -p ~/.termux && grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null"
                + " || echo 'allow-external-apps=true' >> ~/.termux/termux.properties;"
                + " termux-reload-settings; termux-setup-storage";
    }
}
