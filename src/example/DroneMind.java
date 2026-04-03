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
                // Función heurística de costos: (solo toma un parámetro "tile" de destino)
                to -> {
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
                            ConveyorBuild cb = (ConveyorBuild) to.build;
                            
                            // Verificar qué material lleva
                            boolean hasSameItem = false;
                            boolean hasOtherItem = false;
                            
                            for (int i = 0; i < cb.len; i++) {
                                if (cb.ids[i] == targetItem) {
                                    hasSameItem = true;
                                } else {
                                    hasOtherItem = true;
                                }
                            }

                            // Si lleva el MISMO material, ¡genial! unámonos a esta cinta. Costo bajísimo.
                            if (hasSameItem && !hasOtherItem) {
                                return -5f; // Recompensa por reusar cinta!
                            } 
                            // Si lleva un material DISTINTO, necesitamos un Cruce (Junction). Costo Medio.
                            else if (hasOtherItem) {
                                return 15f; 
                            }
                            // Si está vacía, asumimos que es nuestra o de alguien más. Costo bajo.
                            else {
                                return 5f; 
                            }
                        }
                        
                        // Si es otra cosa (Enrutador, Fábrica, etc) es mejor esquivarlo
                        return 100f; 
                    }

                    // Suelo libre
                    return 2f;
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
        // Ignorar la primera (Taladro) y última casilla (Núcleo)
        for (int i = 1; i < path.size - 1; i++) {
            Tile current = path.get(i);
            Tile next = path.get(i + 1);

            // Calcular rotación basándose en el siguiente Tile
            int rotation = current.relativeTo(next.x, next.y);

            // Si el bloque actual YA es una cinta con un ítem DIFERENTE, ponemos un Junction
            boolean needsJunction = false;
            if (current.build instanceof ConveyorBuild && current.team() == team) {
                ConveyorBuild cb = (ConveyorBuild) current.build;
                for (int j = 0; j < cb.len; j++) {
                    if (cb.ids[j] != null && cb.ids[j] != targetItem) {
                        needsJunction = true;
                        break;
                    }
                }
            }

            // Añadir el plan a la cola del equipo para que los Drones lo construyan
            BlockPlan plan;
            if (needsJunction) {
                plan = new BlockPlan(current.x, current.y, (short)rotation, Blocks.junction, null);
            } else {
                // Si la casilla está vacía o es una cinta de nuestro mismo material, la sobreescribimos / giramos.
                plan = new BlockPlan(current.x, current.y, (short)rotation, Blocks.conveyor, null);
            }
            
            // Verificamos si no existe ya este plan exacto en la cola
            boolean found = false;
            for (BlockPlan p : team.data().plans) {
                if (p.x == plan.x && p.y == plan.y) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                team.data().plans.addLast(plan);
            }
        }
    }
}
