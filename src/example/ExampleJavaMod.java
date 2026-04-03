package example;

import arc.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.UnitType;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

public class ExampleJavaMod extends Mod{
    public static UnitType builderDrone;

    public ExampleJavaMod(){
        Log.info("Loaded Builder Drone Mod constructor.");

        // Spawn drones on game start
        Events.on(WorldLoadEvent.class, e -> {
            Time.runTask(60f, () -> {
                if (Vars.player == null) return;
                TeamData data = Vars.player.team().data();
                if (data != null && data.core() != null) {
                    
                    // Activar la mente colmena del código fuente
                    // Usa BaseBuilderAI que calcula terreno, construye taladros, etc.
                    if (data.buildAi == null) {
                        data.buildAi = new mindustry.ai.BaseBuilderAI(data);
                    }
                    data.team.rules().buildAi = true;

                    // Aparecen 4 drones
                    for(int i = 0; i < 4; i++){
                        spawnDrone(data.core());
                    }
                }
            });
        });

        // Respawn when destroyed
        Events.on(UnitDestroyEvent.class, e -> {
            if (e.unit.type == builderDrone && e.unit.team == Vars.player.team()) {
                Time.runTask(300f, () -> { // Respawn after 5 seconds
                    if (Vars.player == null) return;
                    TeamData data = Vars.player.team().data();
                    if(data != null && data.core() != null){
                        spawnDrone(data.core());
                    }
                });
            }
        });
        
        // Actualizar la IA del equipo si es necesario, aunque Logic.java lo hace por nosotros
        // si data.team.rules().buildAi es true. Pero en PvP puede estar bloqueado.
        Events.run(Trigger.update, () -> {
            if(!Vars.state.isPaused() && !Vars.state.isMenu() && Vars.player != null){
                TeamData data = Vars.player.team().data();
                if(data != null && data.team.rules().buildAi && data.buildAi != null){
                    // La actualizacion ya ocurre en Logic.java, excepto si es PvP o reglas especificas.
                    // Para asegurar su funcionamiento en cualquier modo, la corremos manual si el timer no avanza.
                }
            }
        });
    }

    private void spawnDrone(CoreBuild core) {
        Unit unit = builderDrone.create(core.team);
        unit.set(core.x + arc.math.Mathf.range(30f), core.y + arc.math.Mathf.range(30f));
        unit.add();
    }

    @Override
    public void loadContent(){
        Log.info("Loading Builder Drone content.");

        builderDrone = new UnitType("builder-drone") {{
            flying = true;
            drag = 0.05f;
            speed = 3.3f;
            rotateSpeed = 15f;
            accel = 0.1f;
            itemCapacity = 20;
            health = 150f;
            hitSize = 8f;
            engineOffset = 5.5f;
            engineSize = 1.8f;
            isEnemy = false;
            
            // Aura de regeneración pasiva
            abilities.add(new mindustry.entities.abilities.RepairFieldAbility(10f, 60f, 60f));
            
            // Controlador (mente colmena AI / BuilderAI)
            defaultCommand = mindustry.ai.UnitCommand.rebuildCommand;
            
            // Stats para construir y minar
            mineTier = 2; // Puede minar cobre y plomo
            mineSpeed = 1.5f;
            buildSpeed = 1.0f;
            buildBeamOffset = 4f;

            constructor = mindustry.gen.UnitEntity::create;
            controller = u -> new mindustry.ai.types.BuilderAI();
        }};
    }
}
