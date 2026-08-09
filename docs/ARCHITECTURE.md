# ARCHITECTURE

One thesis, four diagrams: **the mind is never uploaded** — the brain stays in the real world and connects to the Matrix over live I/O. Everything else is a consequence of that decision.

## Package map

| Package | Responsibility | Key types |
|---|---|---|
| `matrix.core` | Engine: tick, world, events, determinism | `World`, `Director`, `EventBus`, `Rng`, `SystemState` |
| `matrix.realworld` | OUTSIDE the Matrix — the biological layer | `Brain`, `Pod`, `PodFarm`, `NeuralLink` |
| `matrix.machine` | Machine authority | `Source`, `Architect`, `MachineCity`, `ComputeModel` |
| `matrix.entities` | INSIDE the Matrix — everyone on the field | `Avatar`, `Program`, `Agent`, `SmithPrime`, `SmithCopy`, `TheOne` |
| `matrix.sim` | Presentation | `TerminalRenderer`, `Main` |

Package boundary = deployment boundary: `realworld` knows no entity behavior, `entities` knows no pod details. The only bridge is `NeuralLink`.

## Class diagram — who is human, who is program

```mermaid
classDiagram
    class MatrixEntity {
        <<abstract>>
        +int id
        +int x
        +int y
        +boolean alive
        +tick(World)*
        +glyph()* char
        +renderPriority()* int
    }
    class Chooses { <<interface>> }
    class SelfReplicating { <<interface>> }

    class Avatar {
        +Brain brain
        +Pill pill
        +die(World)
    }
    class TheOne
    class Program {
        <<abstract>>
        +String purpose
        +handleDeletion(World)
    }
    class WorkerProgram { +boolean sati }
    class Agent { +String name }
    class AgentSmith { +handleDeletion(World) throws! }
    class SmithPrime
    class SmithCopy { +MatrixEntity original }
    class Oracle
    class ExileProgram { +Kind kind }

    MatrixEntity <|-- Avatar
    MatrixEntity <|-- Program
    MatrixEntity <|-- SmithCopy
    Avatar <|-- TheOne : the anomaly
    Program <|-- WorkerProgram
    Program <|-- Agent
    Program <|-- Oracle
    Program <|-- ExileProgram
    Program <|-- SmithPrime
    Agent <|-- AgentSmith : refuses GC
    Chooses <|.. Avatar
    Chooses <|.. SmithPrime : added at runtime
    SelfReplicating <|.. SmithPrime
    SelfReplicating <|.. SmithCopy
    SmithCopy *-- MatrixEntity : original kept inside
```

The critical distinction: `Avatar.brain` lives **in the real world** (see deployment), while a `Program`'s hardware is on the machine side. Neo descends from the human line and never becomes a program; Smith is `Program`-line from start to finish. The two hierarchies never cross.

## Sequence — jack-in and the death rule

```mermaid
sequenceDiagram
    participant B as Brain (in pod)
    participant NL as NeuralLink (jack)
    participant W as World (Matrix)
    B->>NL: jackIn(pill)
    NL->>W: spawn(Avatar) — the brain's PROXY
    loop live I/O — no upload
        W-->>B: sensory stream
        B-->>W: neural telemetry
    end
    alt the avatar dies inside the Matrix
        W->>B: Avatar.die() → brain.flatline()
        B->>B: PodFarm.flush(pod) — "the body cannot live without the mind"
    end
```

## Sequence — Smith infection and restore (Decorator, D-001)

```mermaid
sequenceDiagram
    participant S as SmithPrime
    participant W as World
    participant V as Victim (Avatar/Program)
    participant N as TheOne
    S->>W: replace(V, new SmithCopy(V))
    Note over W,V: V leaves the list but is NOT destroyed —<br/>kept in the SmithCopy.original field
    N->>W: replace(copy, copy.original)
    Note over W,V: the same object returns, zero state loss —<br/>the mass restore in the finale depends on this
```

## State machine — system states

```mermaid
stateDiagram-v2
    [*] --> NORMAL : boot v6.0
    NORMAL --> NORMAL : Architect reload<br/>(Zion purge, version++)
    NORMAL --> NEGOTIATION : overflow ≥ 62% ∧ The One on the field
    NORMAL --> NORMAL : overflow ∧ no One →<br/>EMERGENCY reload
    NEGOTIATION --> PEACE : treaty — mass restore,<br/>opt-out, Sati, version++
    PEACE --> NORMAL : peace period ends<br/>(the door stays open)
```

The narrative loop (run by the Director): anomaly builds → **The One is born** → the Source deprecates Smith → `DeletionRefusedException` → **SmithPrime** → exponential spread → the state transitions above.
