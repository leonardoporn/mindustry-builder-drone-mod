package example;

import arc.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.Blocks;
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
                    
                    // ELIMINADO: BaseBuilderAI porque es la IA enemiga que da recursos infinitos 
                    // y construye planos aleatorios.
                    data.team.rules().buildAi = false; 

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
                Time.runTask(300f, () -> { // Respawn after 5 segundos
                    if (Vars.player == null) return;
                    TeamData data = Vars.player.team().data();
                    if(data != null && data.core() != null){
                        spawnDrone(data.core());
                    }
                });
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
            speed = 3.5f; // Más rápidos para reaccionar antes
            rotateSpeed = 19f;
            accel = 0.12f;
            itemCapacity = 30; // Mayor capacidad de minado
            health = 150f;
            hitSize = 8f;
            engineOffset = 5.5f;
            engineSize = 1.8f;
            isEnemy = false;
            
            // Aura de regeneración pasiva (sana estructuras rotas sin consumirse)
            abilities.add(new mindustry.entities.abilities.RepairFieldAbility(15f, 60f, 60f));
            
            // Stats para construir y minar
            mineTier = 2; // Puede minar cobre y plomo
            mineSpeed = 2.5f; // Minado rápido
            buildSpeed = 1.5f;
            buildBeamOffset = 4f;

            constructor = mindustry.gen.UnitEntity::create;
            
            // Asignamos una IA híbrida personalizada:
            // Construye y repara (BuilderAI) si hay cosas por hacer.
            // Mina (MinerAI) si está ocioso, resolviendo el problema de recursos.
            controller = u -> new mindustry.ai.types.BuilderAI() {
                mindustry.ai.types.MinerAI miner = new mindustry.ai.types.MinerAI();
                
                @Override
                public void updateUnit() {
                    super.updateUnit();
                    miner.unit(unit);
                }

                @Override
                public void updateMovement() {
                    // Si el equipo tiene planos en cola o el jugador mandó a construir/reparar
                    if(unit.buildPlan() != null || unit.team.data().plans.size > 0 || unit.activelyBuilding()) {
                        super.updateMovement();
                    } else {
                        // Si no hay nada que construir, ponte a minar automáticamente
                        miner.updateMovement();
                    }
                }
            };
        }};
    }
}
