//         ___ _             _             ____   ___ ____   __
// __  __ / _ \ |_   _  __ _(_)_ __  ___  |___ \ / _ \___ \ / /_
// \ \/ // /_)/ | | | |/ _` | | '_ \/ __|   __) | | | |__) | '_ \
//  >  </ ___/| | |_| | (_| | | | | \__ \  / __/| |_| / __/| (_) |
// / _/\_\/    |_|\__,_|\__, |_|_| |_|___/ |_____|\___/_____|\___/
//                    |___/
package es.xplugins.xtpa.api;
// API DE XTPA BY XPLUGINS
// API PARA USO DE OTROS COMPLETOS
// TOTALMENTE GRATIS GRADLE/POM/KOTLIN
// Y MUCHO MAS REPOSITORIO EN BREVE.
import org.bukkit.entity.Player;

import java.util.UUID;

public class xTpaAPI {

    private static xTpaAPI instance;
    private final es.xplugins.xtpa.xTpa plugin;

    public xTpaAPI(es.xplugins.xtpa.xTpa plugin) {
        this.plugin = plugin;
        instance = this;
    }

    /**
     *
     * Obtiene la instancia activa de la API.
     *
     */
    public static xTpaAPI getInstance() {
        return instance;
    }

    /**
     *
     * Verifica si un jugador tiene una petición pendiente de teletransporte (como objetivo).
     *
     */
    public boolean hasPendingRequest(UUID target) {
        return plugin.getTpaRequests().containsKey(target);
    }

    /**
     *
     * Obtiene el UUID de quien le envió la petición a un jugador.
     *
     */
    public UUID getSenderOfRequest(UUID target) {
        return plugin.getTpaRequests().get(target);
    }

    /**
     *
     * Crea una petición de TPA forzada por API.
     *
     */
    public void createRequest(Player sender, Player target) {

        plugin.getTpaRequests().put(target.getUniqueId(), sender.getUniqueId());

        plugin.getTpaTimes().put(target.getUniqueId(), System.currentTimeMillis());

    }
}