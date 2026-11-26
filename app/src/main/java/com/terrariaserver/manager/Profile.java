package com.terrariaserver.manager;

import org.json.JSONObject;

public class Profile {
    public String id = Long.toString(System.currentTimeMillis(), 36);
    public String name = "Meu servidor";
    public int version = 0;
    public String world = "Mundo";
    public int size = 1;          // 0 pequeno, 1 medio, 2 grande
    public int difficulty = 0;    // 0 classico, 1 expert, 2 mestre, 3 jornada
    public int seedPreset = 0;
    public String seedCustom = "";
    public int maxPlayers = 8;
    public int port = 7777;
    public String password = "";
    public String motd = "Bem-vindo ao servidor!";
    public int language = 0;
    public boolean secure = true;
    public boolean upnp = false;
    public boolean smoothNpc = true;
    public int journey = 2;

    public String versionCode() {
        return Catalog.VERSION_CODES[version];
    }

    public String versionName() {
        return Catalog.VERSION_LABELS[version].replace(" (mais recente)", "");
    }

    public String worldFile() {
        String w = world.replaceAll("[^A-Za-z0-9_-]", "_");
        return w.isEmpty() ? "Mundo" : w;
    }

    public String seed() {
        if (seedPreset == Catalog.SEED_CUSTOM) {
            return seedCustom.trim();
        }
        return Catalog.SEED_CODES[seedPreset];
    }

    public String dir() {
        return "/root/servers/" + id;
    }

    /** serverconfig.txt oficial do Terraria (caminhos dentro do Debian/proot). */
    public String config() {
        StringBuilder b = new StringBuilder();
        b.append("# Gerado pelo app Terraria Server\n");
        b.append("worldpath=").append(dir()).append("/worlds\n");
        b.append("world=").append(dir()).append("/worlds/").append(worldFile()).append(".wld\n");
        b.append("worldname=").append(worldFile()).append("\n");
        b.append("autocreate=").append(size + 1).append("\n");
        if (!seed().isEmpty()) {
            b.append("seed=").append(seed()).append("\n");
        }
        b.append("difficulty=").append(difficulty).append("\n");
        b.append("maxplayers=").append(maxPlayers).append("\n");
        b.append("port=").append(port).append("\n");
        b.append("password=").append(password).append("\n");
        b.append("motd=").append(motd).append("\n");
        b.append("language=").append(Catalog.LANG_CODES[language]).append("\n");
        b.append("secure=").append(secure ? 1 : 0).append("\n");
        b.append("upnp=").append(upnp ? 1 : 0).append("\n");
        b.append("npcstream=").append(smoothNpc ? 60 : 0).append("\n");
        b.append("banlist=").append(dir()).append("/banlist.txt\n");
        if (difficulty == 3) {
            for (String k : Catalog.JOURNEY_KEYS) {
                b.append("journeypermission_").append(k).append("=").append(journey).append("\n");
            }
        }
        return b.toString();
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("version", version);
            o.put("world", world);
            o.put("size", size);
            o.put("difficulty", difficulty);
            o.put("seedPreset", seedPreset);
            o.put("seedCustom", seedCustom);
            o.put("maxPlayers", maxPlayers);
            o.put("port", port);
            o.put("password", password);
            o.put("motd", motd);
            o.put("language", language);
            o.put("secure", secure);
            o.put("upnp", upnp);
            o.put("smoothNpc", smoothNpc);
            o.put("journey", journey);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Profile fromJson(JSONObject o) {
        Profile p = new Profile();
        p.id = o.optString("id", p.id);
        p.name = o.optString("name", p.name);
        p.version = Math.max(0, Math.min(Catalog.VERSION_CODES.length - 1, o.optInt("version", 0)));
        p.world = o.optString("world", p.world);
        p.size = o.optInt("size", 1);
        p.difficulty = o.optInt("difficulty", 0);
        p.seedPreset = o.optInt("seedPreset", 0);
        p.seedCustom = o.optString("seedCustom", "");
        p.maxPlayers = o.optInt("maxPlayers", 8);
        p.port = o.optInt("port", 7777);
        p.password = o.optString("password", "");
        p.motd = o.optString("motd", "");
        p.language = o.optInt("language", 0);
        p.secure = o.optBoolean("secure", true);
        p.upnp = o.optBoolean("upnp", false);
        p.smoothNpc = o.optBoolean("smoothNpc", true);
        p.journey = o.optInt("journey", 2);
        return p;
    }
}
