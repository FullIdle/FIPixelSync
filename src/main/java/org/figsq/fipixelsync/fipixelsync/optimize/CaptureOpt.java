package org.figsq.fipixelsync.fipixelsync.optimize;

import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import lombok.val;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerCaptureMessage;

public class CaptureOpt {
    public static void onCapture(CaptureEvent.SuccessfulCapture event) {
        val ep = event.getPokemon();
        val pokemonData = ep.getPokemonData();
        CommManager.publish(new PlayerCaptureMessage(
                event.player.func_110124_au(),
                pokemonData
        ));
    }
}
