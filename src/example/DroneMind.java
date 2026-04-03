package example;

import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.game.Teams.BlockPlan;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Conveyor.ConveyorBuild;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.ai.Astar;

public class DroneMind {

    // Solo corre 1 cálculo por vez para no generar Lag
    private static boolean isCalculating = false;
    private static long lastCalcTime = 0;

    /**
     * Intenta trazar una ruta de cintas desde un taladro hasta el núcleo.
     * @param startTile El azulejo de origen (Drill)
     * @param targetItem El ítem principal que producirá este origen
     * @param team El equipo
     */
    public static void tryBuildConveyorPath(Tile startTile, Item targetItem, Team team) {
        if(isCalculating || System.currentTimeMillis() - lastCalcTime < 1000) {
            return; // Esperar 1 segundo entre cálculos globales para no congelar el juego
        }

        CoreBuild core = team.core();
        if(core == null || startTile == null || targetItem == null) return;

        isCalculating = true;

        try {
            Seq<Tile> path = Astar.pathfind(startTile, core.tile, 
                // Función heurística de costos
                new Astar.TileHeuristic() {
                    @Override
                    public float cost(Tile tile) {
                        return 0f; // Nunca será llamado si reescribimos el de abajo
                    }

                    @Override
                    public float cost(Tile from, Tile to) {
                        // Si es el núcleo, llegamos! Costo 0
                        if (to.build instanceof CoreBuild) return 0f;

                    // Si el bloque es sólido o no caminable (muro de fábrica, agua profunda)
                    if (to.solid() || to.floor().isDeep()) {
                        return 100000f; // Infranqueable
                    }

                    // Si ya hay un bloque aliado construido
                    if (to.build != null && to.team() == team) {
                        // Es una cinta transportadora
                        if (to.build instanceof ConveyorBuild) {
                            int dir = from.relativeTo(to.x, to.y);
                            
                            // Si va en la misma dirección, es ideal unirse
                            if (to.build.rotation == dir) {
                                return -10f; 
                            } 
                            // Si es perpendicular, se necesita un Cruce (Junction). Penalización media.
                            else if (to.build.rotation % 2 != dir % 2) {
                                return 15f; 
                            } 
                            // Si va en dirección opuesta, hay colisión frontal, evitar a toda costa.
                            else {
                                return 80f; 
                            }
                        }
                        
                        // Si es otra cosa (Enrutador, Fábrica, etc) es mejor esquivarlo
                        return 100f; 
                    }

                    // Suelo libre
                    return 2f;
                }
            },
                // Passable check
                tile -> tile != null && tile.floor() != null
            );

            // Si encontró un camino
            if(path.size > 0) {
                // Convertir la ruta en BlockPlans para el equipo
                createPlansFromPath(path, targetItem, team);
            }
            
        } catch (Exception e) {
            // Ignorar errores de pathfinding
        } finally {
            lastCalcTime = System.currentTimeMillis();
            isCalculating = false;
        }
    }

    private static void createPlansFromPath(Seq<Tile> path, Item targetItem, Team team) {
        // Encontrar el primer azulejo que esté fuera del taladro
        int startIndex = 0;
        while(startIndex < path.size && path.get(startIndex).build == path.get(0).build) {
            startIndex++;
        }
        
        // Encontrar el último azulejo que esté fuera del núcleo
        int endIndex = path.size - 1;
        while(endIndex >= 0 && path.get(endIndex).build == path.get(path.size - 1).build) {
            endIndex--;
        }

        // Si la ruta es muy corta (pegado al núcleo)
        if (startIndex > endIndex) return;

        for (int i = startIndex; i <= endIndex; i++) {
            Tile current = path.get(i);
            Tile next;
            
            if (i < path.size - 1) {
                next = path.get(i + 1);
            } else {
                next = current; // El último bloque apuntará hacia donde apuntaba antes
            }

            // Calcular rotación basándose en el siguiente Tile (hacia donde vamos)
            int rotation = current.relativeTo(next.x, next.y);
            if (rotation == -1) {
                // Si por alguna razón es -1 (mismo tile), intentar con el núcleo
                Tile coreTile = path.get(path.size - 1);
                rotation = current.relativeTo(coreTile.x, coreTile.y);
                if (rotation == -1) rotation = 0;
            }

            // Verificar si el bloque actual o plano actual es una cinta en OTRA dirección
            boolean needsJunction = false;
            
            // 1. Verificamos bloque ya construido en el mapa
            if (current.build instanceof ConveyorBuild && current.team() == team) {
                if (current.build.rotation != rotation) {
                    needsJunction = true; // Es perpendicular u opuesta, cruzar
                }
            }

            // 2. Verificamos planos "fantasma" que otros drones están por construir
            for (BlockPlan p : team.data().plans) {
                if (p.x == current.x && p.y == current.y && p.block == Blocks.conveyor) {
                    if (p.rotation != rotation) {
                        needsJunction = true; // Cruzar con el fantasma también
                    }
                    break;
                }
            }

            // Añadir el plan a la cola del equipo para que los Drones lo construyan
            BlockPlan plan;
            if (needsJunction) {
                plan = new BlockPlan(current.x, current.y, (short)rotation, Blocks.junction, null);
            } else {
                plan = new BlockPlan(current.x, current.y, (short)rotation, Blocks.conveyor, null);
            }
            
            // Verificamos si no existe ya este plan exacto en la cola, si hay otro diferente lo borramos
            boolean found = false;
            for (int j = 0; j < team.data().plans.size; j++) {
                BlockPlan p = team.data().plans.get(j);
                if (p.x == plan.x && p.y == plan.y) {
                    if (p.block != plan.block || p.rotation != plan.rotation) {
                        team.data().plans.removeIndex(j);
                        j--; // Ajustar índice tras borrar
                    } else {
                        found = true;
                    }
                }
            }

            if (!found) {
                team.data().plans.addLast(plan);
            }
        }
    }
}
