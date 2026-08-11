package matrix.realworld;

import matrix.core.Geo;
import matrix.core.Position;
import matrix.core.World;
import matrix.entities.Agent;
import matrix.entities.Avatar;

/**
 * What one brain is fed this tick — the system's true output (D-021).
 * Streams as one JSON line (accepted spark: a dream is one jq away).
 * Integers only; built by hand so no formatting is locale-dependent.
 */
public final class PerceptionFrame {

    private PerceptionFrame() {}

    public static String jsonl(long tick, Avatar self, World world) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"tick\":").append(tick)
          .append(",\"who\":\"").append(escape(self.pilotName))
          .append("\",\"pill\":\"").append(self.pill)
          .append("\",\"pos\":[").append(self.xCm()).append(',').append(self.yCm()).append(']');
        Agent agent = world.nearestAgent(self.xCm(), self.yCm());
        if (agent != null) {
            sb.append(",\"nearestAgentCm\":").append(Geo.chebyshevCm(self.xCm(), self.yCm(), agent.xCm(), agent.yCm()));
        }
        Position exit = world.places().nearestExit(self.xCm(), self.yCm());
        sb.append(",\"nearestExitCm\":")
          .append(Geo.chebyshevCm(self.xCm(), self.yCm(), exit.xCm(), exit.yCm()))
          .append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
