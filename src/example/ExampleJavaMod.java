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

        // Trigger conveyor pathfinding when a drill is placed
        Events.on(BlockBuildEndEvent.class, e -> {
            if(!e.breaking && e.tile.build instanceof mindustry.world.blocks.production.Drill.DrillBuild) {
                mindustry.world.blocks.production.Drill.DrillBuild drill = (mindustry.world.blocks.production.Drill.DrillBuild) e.tile.build;
                if(drill.dominantItem != null) {
                    DroneMind.tryBuildConveyorPath(drill.tile, drill.dominantItem, drill.team);
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
            speed = 5.5f; // Más rápidos para reaccionar antes
            rotateSpeed = 19f;
            accel = 0.15f;
            itemCapacity = 40; // Mayor capacidad de minado
            health = 150f;
            hitSize = 8f;
            engineOffset = 5.5f;
            engineSize = 1.8f;
            isEnemy = false;
            
            // Aura de regeneración pasiva (sana estructuras rotas sin consumirse)
            abilities.add(new mindustry.entities.abilities.RepairFieldAbility(15f, 60f, 60f));
            
            // Stats para construir y minar
            mineTier = 0; // Ya no pueden minar para evitar que se distraigan o traben
            mineSpeed = 0f; 
            buildSpeed = 7.0f; // Construcción ultra rápida
            buildBeamOffset = 4f;

            constructor = mindustry.gen.UnitEntity::create;
            
            // Asignamos una IA pura de construcción ultra-reactiva:
            controller = u -> new mindustry.ai.types.BuilderAI() {
                {
                    // Revisa la cola de construcción instantáneamente
                    rebuildPeriod = 2f;
                }
            };
        }};
    }
}
